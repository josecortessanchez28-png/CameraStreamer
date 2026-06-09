package com.camerastreamer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.ByteArrayOutputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class StreamService extends Service {

    private static final String TAG = "StreamService";
    private static final String CHANNEL_ID = "camera_stream";
    private static final int NOTIFICATION_ID = 1;

    private CameraHelper cameraHelper;
    private Camera camera;
    private WebSocket webSocket;
    private OkHttpClient client;
    private String relayUrl;
    private String cameraId;
    private boolean running = false;
    private int frameCount = 0;

    public interface StreamCallback {
        void onFrameCount(int count);
        void onStatus(String status);
    }

    public static StreamCallback staticCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        client = new OkHttpClient();
        cameraId = "android_" + Build.MODEL.replaceAll("\\s+", "_").toLowerCase();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        relayUrl = intent.getStringExtra("relay_url");
        if (relayUrl == null || relayUrl.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        Notification notification = buildNotification("Conectando...");
        startForeground(NOTIFICATION_ID, notification);

        connectWebSocket();
        startCamera();

        return START_STICKY;
    }

    private void connectWebSocket() {
        Request request = new Request.Builder()
                .url(relayUrl)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.d(TAG, "WebSocket connected");
                running = true;

                String registerMsg = "{\"type\":\"register\",\"camera_id\":\"" + cameraId + "\",\"name\":\"Camera " + Build.MODEL + "\"}";
                ws.send(registerMsg);

                updateNotification("Transmitiendo...");
                if (staticCallback != null) {
                    staticCallback.onStatus("Conectado");
                }
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                Log.d(TAG, "Message: " + text);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure: " + t.getMessage());
                running = false;
                updateNotification("Desconectado");
                if (staticCallback != null) {
                    staticCallback.onStatus("Desconectado: " + t.getMessage());
                }

                ws.cancel();
                reconnectAfterDelay();
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + reason);
                running = false;
                updateNotification("Cerrado");
                if (staticCallback != null) {
                    staticCallback.onStatus("Conexión cerrada");
                }
                reconnectAfterDelay();
            }
        });
    }

    private void reconnectAfterDelay() {
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                if (relayUrl != null && !relayUrl.isEmpty()) {
                    connectWebSocket();
                }
            } catch (InterruptedException ignored) {}
        }).start();
    }

    private void startCamera() {
        cameraHelper = new CameraHelper();
        int id = CameraHelper.findBackCamera();
        camera = cameraHelper.open(id);

        if (camera == null) {
            Log.e(TAG, "Could not open camera");
            updateNotification("Error: cámara no disponible");
            if (staticCallback != null) {
                staticCallback.onStatus("Error: cámara no disponible");
            }
            return;
        }

        Camera.Size previewSize = camera.getParameters().getPreviewSize();

        cameraHelper.addCallbackBuffer(new byte[previewSize.width * previewSize.height * 3 / 2]);
        cameraHelper.setPreviewCallback((data, cam) -> {
            if (!running || webSocket == null) return;

            try {
                Camera.Size size = cam.getParameters().getPreviewSize();
                YuvImage yuv = new YuvImage(data, ImageFormat.NV21, size.width, size.height, null);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                yuv.compressToJpeg(new Rect(0, 0, size.width, size.height), 70, out);
                byte[] jpeg = out.toByteArray();

                webSocket.send(ByteString.of(jpeg));

                frameCount++;
                if (staticCallback != null) {
                    staticCallback.onFrameCount(frameCount);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing frame: " + e.getMessage());
            }

            cam.addCallbackBuffer(data);
        });

        try {
            camera.startPreview();
        } catch (Exception e) {
            Log.e(TAG, "Error starting preview: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Camera Stream",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Streaming camera video");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("CameraStreamer")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (webSocket != null) {
            webSocket.close(1000, "Service stopped");
        }
        if (cameraHelper != null) {
            cameraHelper.release();
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
