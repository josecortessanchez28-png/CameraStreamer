"""
CameraStreamer Desktop - Receptor de video WebSocket
Todo-en-uno: recibe, muestra, graba, screenshots, API de agente
"""

import asyncio
import json
import os
import sys
import time
import threading
import queue
import base64
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

import cv2
import numpy as np
from PIL import Image, ImageTk

try:
    import websocket
except ImportError:
    print("Instalando websocket-client...")
    os.system("pip install websocket-client")
    import websocket

import tkinter as tk
from tkinter import ttk, messagebox, filedialog

VERSION = "2.0.0"
DEFAULT_RELAY = "wss://your-relay.glitch.me"
AGENT_PORT = 9001
RECONNECT_DELAY = 5
JPEG_QUALITY = 70


class CameraStream:
    def __init__(self, camera_id, name="Unknown"):
        self.camera_id = camera_id
        self.name = name
        self.frame = None
        self.last_update = 0
        self.frame_count = 0
        self.connected = False
        self.lock = threading.Lock()

    def update_frame(self, jpeg_bytes):
        try:
            nparr = np.frombuffer(jpeg_bytes, np.uint8)
            img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            if img is not None:
                with self.lock:
                    self.frame = img
                    self.last_update = time.time()
                    self.frame_count += 1
                return True
        except Exception as e:
            print(f"Error decoding frame: {e}")
        return False

    def get_frame(self):
        with self.lock:
            return self.frame.copy() if self.frame is not None else None

    def get_info(self):
        with self.lock:
            age = time.time() - self.last_update if self.last_update > 0 else -1
            return {
                "id": self.camera_id,
                "name": self.name,
                "connected": self.connected,
                "frame_count": self.frame_count,
                "last_frame_age": round(age, 1),
                "resolution": f"{self.frame.shape[1]}x{self.frame.shape[0]}" if self.frame is not None else "N/A"
            }


class WebSocketClient:
    def __init__(self, relay_url, on_frame=None, on_status=None):
        self.relay_url = relay_url
        self.on_frame = on_frame
        self.on_status = on_status
        self.ws = None
        self.running = False
        self.thread = None
        self.reconnect = True

    def start(self):
        self.running = True
        self.reconnect = True
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def stop(self):
        self.running = False
        self.reconnect = False
        if self.ws:
            try:
                self.ws.close()
            except:
                pass

    def _run(self):
        while self.running and self.reconnect:
            try:
                if self.on_status:
                    self.on_status("Conectando...")

                self.ws = websocket.WebSocketApp(
                    self.relay_url,
                    on_message=self._on_message,
                    on_error=self._on_error,
                    on_close=self._on_close,
                    on_open=self._on_open
                )
                self.ws.run_forever(ping_interval=30, ping_timeout=10)
            except Exception as e:
                print(f"WebSocket error: {e}")

            if self.running and self.reconnect:
                if self.on_status:
                    self.on_status(f"Reconectando en {RECONNECT_DELAY}s...")
                time.sleep(RECONNECT_DELAY)

    def _on_message(self, ws, message):
        if isinstance(message, bytes):
            if self.on_frame:
                self.on_frame(message)
        else:
            try:
                msg = json.loads(message)
                if msg.get("type") == "registered":
                    print(f"Registered as: {msg.get('camera_id')}")
            except:
                pass

    def _on_error(self, ws, error):
        print(f"WebSocket error: {error}")
        if self.on_status:
            self.on_status(f"Error: {error}")

    def _on_close(self, ws, close_status_code, close_msg):
        print("WebSocket closed")
        if self.on_status:
            self.on_status("Desconectado")

    def _on_open(self, ws):
        print("WebSocket connected")
        if self.on_status:
            self.on_status("Conectado")


class AgentHandler(BaseHTTPRequestHandler):
    desktop_app = None

    def log_message(self, format, *args):
        pass

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        params = parse_qs(parsed.query)

        if path == "/status":
            self._json_response(200, self._get_status())
        elif path == "/frame":
            self._send_frame()
        elif path == "/list/cameras":
            self._json_response(200, self._list_cameras())
        elif path == "/screenshot":
            self._take_screenshot()
        else:
            self._json_response(404, {"error": "not found"})

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/reconnect":
            self._reconnect()
        elif path == "/record":
            self._toggle_record()
        else:
            self._json_response(404, {"error": "not found"})

    def _json_response(self, code, data):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def _get_status(self):
        app = self.desktop_app
        cameras = [cam.get_info() for cam in app.cameras.values()]
        return {
            "version": VERSION,
            "relay_url": app.relay_url,
            "connected": app.connected,
            "cameras": cameras,
            "recording": app.recording,
            "total_frames": sum(cam.frame_count for cam in app.cameras.values())
        }

    def _list_cameras(self):
        return [cam.get_info() for cam in self.desktop_app.cameras.values()]

    def _send_frame(self):
        app = self.desktop_app
        if not app.cameras:
            self._json_response(404, {"error": "no cameras"})
            return

        cam_id = list(app.cameras.keys())[0]
        frame = app.cameras[cam_id].get_frame()
        if frame is None:
            self._json_response(503, {"error": "no frame"})
            return

        _, jpeg = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, JPEG_QUALITY])
        self.send_response(200)
        self.send_header("Content-Type", "image/jpeg")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(jpeg.tobytes())

    def _take_screenshot(self):
        app = self.desktop_app
        app.take_screenshot()
        self._json_response(200, {"status": "ok"})

    def _reconnect(self):
        app = self.desktop_app
        app.reconnect()
        self._json_response(200, {"status": "reconnecting"})

    def _toggle_record(self):
        app = self.desktop_app
        app.toggle_record()
        self._json_response(200, {"recording": app.recording})


class DesktopApp:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title(f"CameraStreamer Desktop v{VERSION}")
        self.root.geometry("800x600")
        self.root.configure(bg="#1a1a2e")

        self.relay_url = DEFAULT_RELAY
        self.ws_client = None
        self.cameras = {}
        self.connected = False
        self.recording = False
        self.video_writer = None
        self.screenshot_dir = "screenshots"
        self.frame_queue = queue.Queue(maxsize=5)

        os.makedirs(self.screenshot_dir, exist_ok=True)

        self._setup_ui()
        self._start_agent_server()
        self._start_frame_processor()

        self.root.protocol("WM_DELETE_WINDOW", self._on_close)
        self.root.bind("d", lambda e: self.take_screenshot())
        self.root.bind("r", lambda e: self.toggle_record())
        self.root.bind("c", lambda e: self.reconnect())

    def _setup_ui(self):
        top_frame = tk.Frame(self.root, bg="#16213e", height=60)
        top_frame.pack(fill=tk.X)
        top_frame.pack_propagate(False)

        tk.Label(top_frame, text="Relay:", bg="#16213e", fg="white",
                 font=("Arial", 10)).pack(side=tk.LEFT, padx=(10, 5))

        self.entry_url = tk.Entry(top_frame, width=40, font=("Arial", 10))
        self.entry_url.insert(0, self.relay_url)
        self.entry_url.pack(side=tk.LEFT, padx=5)

        self.btn_connect = tk.Button(top_frame, text="Conectar", command=self.connect,
                                     bg="#0f3460", fg="white", font=("Arial", 10, "bold"))
        self.btn_connect.pack(side=tk.LEFT, padx=5)

        self.btn_disconnect = tk.Button(top_frame, text="Desconectar", command=self.disconnect,
                                        bg="#e94560", fg="white", font=("Arial", 10, "bold"),
                                        state=tk.DISABLED)
        self.btn_disconnect.pack(side=tk.LEFT, padx=5)

        self.btn_record = tk.Button(top_frame, text="Grabar", command=self.toggle_record,
                                    bg="#533483", fg="white", font=("Arial", 10, "bold"),
                                    state=tk.DISABLED)
        self.btn_record.pack(side=tk.LEFT, padx=5)

        self.btn_screenshot = tk.Button(top_frame, text="Screenshot [d]", command=self.take_screenshot,
                                        bg="#2b6777", fg="white", font=("Arial", 10, "bold"),
                                        state=tk.DISABLED)
        self.btn_screenshot.pack(side=tk.LEFT, padx=5)

        self.video_frame = tk.Frame(self.root, bg="#0a0a23")
        self.video_frame.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)

        self.canvas = tk.Canvas(self.video_frame, bg="#0a0a23", highlightthickness=0)
        self.canvas.pack(fill=tk.BOTH, expand=True)

        self.label = tk.Label(self.video_frame, text="Sin conexión", bg="#0a0a23", fg="#aaa",
                              font=("Arial", 14))
        self.label.place(relx=0.5, rely=0.5, anchor="center")

        bottom_frame = tk.Frame(self.root, bg="#16213e", height=30)
        bottom_frame.pack(fill=tk.X)
        bottom_frame.pack_propagate(False)

        self.status_label = tk.Label(bottom_frame, text="Desconectado", bg="#16213e", fg="#e94560",
                                     font=("Arial", 9))
        self.status_label.pack(side=tk.LEFT, padx=10)

        self.fps_label = tk.Label(bottom_frame, text="FPS: 0", bg="#16213e", fg="#aaa",
                                  font=("Arial", 9))
        self.fps_label.pack(side=tk.RIGHT, padx=10)

        self.frames_label = tk.Label(bottom_frame, text="Frames: 0", bg="#16213e", fg="#aaa",
                                     font=("Arial", 9))
        self.frames_label.pack(side=tk.RIGHT, padx=10)

    def _start_agent_server(self):
        AgentHandler.desktop_app = self
        self.agent_server = HTTPServer(("127.0.0.1", AGENT_PORT), AgentHandler)
        self.agent_thread = threading.Thread(target=self.agent_server.serve_forever, daemon=True)
        self.agent_thread.start()
        print(f"Agent API running on http://127.0.0.1:{AGENT_PORT}")

    def _start_frame_processor(self):
        def process():
            try:
                while True:
                    try:
                        jpeg_data = self.frame_queue.get(timeout=0.1)
                        cam_id = jpeg_data[0]
                        frame_bytes = jpeg_data[1]

                        if cam_id not in self.cameras:
                            self.cameras[cam_id] = CameraStream(cam_id, f"Camera {cam_id}")

                        self.cameras[cam_id].update_frame(frame_bytes)
                        self._update_display()

                        if self.recording and self.video_writer is not None:
                            frame = self.cameras[cam_id].get_frame()
                            if frame is not None:
                                self.video_writer.write(frame)

                    except queue.Empty:
                        continue
            except:
                pass

        self.processor_thread = threading.Thread(target=process, daemon=True)
        self.processor_thread.start()

    def _on_frame(self, jpeg_bytes):
        try:
            cam_id = "default"
            self.frame_queue.put_nowait((cam_id, jpeg_bytes))
        except queue.Full:
            pass

    def _on_status(self, status):
        self.root.after(0, lambda: self.status_label.configure(text=status))
        if "Conectado" == status:
            self.connected = True
            self.root.after(0, self._update_buttons)
        elif "Desconectado" in status or "Error" in status:
            self.connected = False
            self.root.after(0, self._update_buttons)

    def _update_display(self):
        try:
            if not self.cameras:
                return

            cam_id = list(self.cameras.keys())[0]
            frame = self.cameras[cam_id].get_frame()
            if frame is None:
                return

            canvas_w = self.canvas.winfo_width()
            canvas_h = self.canvas.winfo_height()
            if canvas_w <= 1 or canvas_h <= 1:
                return

            h, w = frame.shape[:2]
            scale = min(canvas_w / w, canvas_h / h)
            new_w = int(w * scale)
            new_h = int(h * scale)

            resized = cv2.resize(frame, (new_w, new_h))
            rgb = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB)
            img = Image.fromarray(rgb)
            imgtk = ImageTk.PhotoImage(image=img)

            self.root.after(0, lambda: self._draw_frame(imgtk, new_w, new_h))

            info = self.cameras[cam_id].get_info()
            self.root.after(0, lambda: self.frames_label.configure(
                text=f"Frames: {info['frame_count']} | {info['resolution']}"))

        except Exception as e:
            print(f"Display error: {e}")

    def _draw_frame(self, imgtk, w, h):
        self.canvas.delete("all")
        canvas_w = self.canvas.winfo_width()
        canvas_h = self.canvas.winfo_height()
        x = (canvas_w - w) // 2
        y = (canvas_h - h) // 2
        self.canvas.create_image(x, y, anchor=tk.NW, image=imgtk)
        self.canvas.image = imgtk

    def _update_buttons(self):
        if self.connected:
            self.btn_connect.configure(state=tk.DISABLED)
            self.btn_disconnect.configure(state=tk.NORMAL)
            self.btn_record.configure(state=tk.NORMAL)
            self.btn_screenshot.configure(state=tk.NORMAL)
        else:
            self.btn_connect.configure(state=tk.NORMAL)
            self.btn_disconnect.configure(state=tk.DISABLED)
            self.btn_record.configure(state=tk.DISABLED)
            self.btn_screenshot.configure(state=tk.DISABLED)

    def connect(self):
        self.relay_url = self.entry_url.get().strip()
        if not self.relay_url:
            messagebox.showerror("Error", "Introduce la URL del relay")
            return

        if self.ws_client:
            self.disconnect()

        self.ws_client = WebSocketClient(
            self.relay_url,
            on_frame=self._on_frame,
            on_status=self._on_status
        )
        self.ws_client.start()

    def disconnect(self):
        if self.ws_client:
            self.ws_client.stop()
            self.ws_client = None
        self.connected = False
        self.cameras.clear()
        self._update_buttons()
        self.label.place(relx=0.5, rely=0.5, anchor="center")
        self.canvas.delete("all")

    def reconnect(self):
        self.disconnect()
        self.connect()

    def take_screenshot(self):
        if not self.cameras:
            return

        cam_id = list(self.cameras.keys())[0]
        frame = self.cameras[cam_id].get_frame()
        if frame is None:
            return

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = os.path.join(self.screenshot_dir, f"screenshot_{timestamp}.jpg")
        cv2.imwrite(filename, frame)
        print(f"Screenshot saved: {filename}")

    def toggle_record(self):
        if not self.cameras:
            return

        if self.recording:
            self.recording = False
            if self.video_writer:
                self.video_writer.release()
                self.video_writer = None
            self.btn_record.configure(text="Grabar", bg="#533483")
        else:
            cam_id = list(self.cameras.keys())[0]
            frame = self.cameras[cam_id].get_frame()
            if frame is None:
                return

            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = os.path.join(self.screenshot_dir, f"recording_{timestamp}.avi")
            h, w = frame.shape[:2]
            fourcc = cv2.VideoWriter_fourcc(*'XVID')
            self.video_writer = cv2.VideoWriter(filename, fourcc, 10, (w, h))
            self.recording = True
            self.btn_record.configure(text="Detener", bg="#e94560")

    def _on_close(self):
        if self.ws_client:
            self.ws_client.stop()
        if self.agent_server:
            self.agent_server.shutdown()
        if self.video_writer:
            self.video_writer.release()
        self.root.destroy()

    def run(self):
        self.root.mainloop()


if __name__ == "__main__":
    print(f"CameraStreamer Desktop v{VERSION}")
    print(f"Agent API: http://127.0.0.1:{AGENT_PORT}")
    print("Atajos: d=screenshot, r=grabar, c=reconectar")
    app = DesktopApp()
    app.run()
