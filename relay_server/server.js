const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const os = require('os');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

const PORT = process.env.PORT || 3000;

const senders = new Map();
const receivers = new Set();

let frameCount = 0;
let lastFpsTime = Date.now();
let currentFps = 0;

app.get('/', (req, res) => {
  res.json({
    status: 'ok',
    service: 'CameraStreamer Relay',
    senders: Array.from(senders.values()).map(s => ({
      id: s.id,
      name: s.name,
      connected: s.ws.readyState === WebSocket.OPEN
    })),
    receivers: receivers.size,
    fps: currentFps,
    totalFrames: frameCount
  });
});

app.get('/health', (req, res) => {
  res.json({ ok: true, uptime: process.uptime() });
});

wss.on('connection', (ws, req) => {
  const ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
  console.log(`[CONNECT] ${ip}`);

  let isSender = false;
  let senderId = null;

  ws.on('message', (data, isBinary) => {
    if (isBinary) {
      frameCount++;

      const now = Date.now();
      if (now - lastFpsTime >= 1000) {
        currentFps = frameCount;
        frameCount = 0;
        lastFpsTime = now;
      }

      for (const receiver of receivers) {
        if (receiver.readyState === WebSocket.OPEN) {
          receiver.send(data);
        }
      }
    } else {
      try {
        const msg = JSON.parse(data.toString());

        if (msg.type === 'register') {
          isSender = true;
          senderId = msg.camera_id || `cam_${Date.now()}`;
          senders.set(senderId, {
            id: senderId,
            name: msg.name || senderId,
            ws: ws
          });
          console.log(`[SENDER] Registered: ${senderId} (${msg.name})`);

          ws.send(JSON.stringify({
            type: 'registered',
            camera_id: senderId,
            status: 'ok'
          }));
        }

        if (msg.type === 'command') {
          for (const [id, sender] of senders) {
            if (sender.ws.readyState === WebSocket.OPEN) {
              sender.ws.send(JSON.stringify(msg));
            }
          }
        }
      } catch (e) {
      }
    }
  });

  ws.on('close', () => {
    console.log(`[DISCONNECT] ${ip}`);
    if (isSender && senderId) {
      senders.delete(senderId);
      console.log(`[SENDER] Removed: ${senderId}`);
    }
    receivers.delete(ws);
  });

  ws.on('error', (err) => {
    console.log(`[ERROR] ${ip}: ${err.message}`);
  });

  setTimeout(() => {
    if (!isSender) {
      receivers.add(ws);
      console.log(`[RECEIVER] Added (total: ${receivers.size})`);
    }
  }, 100);
});

setInterval(() => {
  for (const receiver of receivers) {
    if (receiver.readyState !== WebSocket.OPEN) {
      receivers.delete(receiver);
    }
  }
}, 30000);

server.listen(PORT, () => {
  console.log(`CameraStreamer Relay running on port ${PORT}`);
  console.log(`Status: http://localhost:${PORT}`);
});
