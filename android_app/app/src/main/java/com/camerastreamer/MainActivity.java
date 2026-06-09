package com.camerastreamer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class MainActivity extends AppCompatActivity {

    private EditText etRelayUrl;
    private Button btnStart;
    private Button btnStop;
    private TextView tvStatus;
    private TextView tvFrames;
    private SurfaceView surfaceView;

    private CameraHelper cameraHelper;
    private Camera camera;
    private OkHttpClient wsClient;
    private volatile WebSocket webSocket;
    private volatile boolean streaming = false;
    private int frameCount = 0;
    private SurfaceHolder.Callback surfaceCallback;
    private int previewWidth;
    private int previewHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etRelayUrl = findViewById(R.id.et_relay_url);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        tvStatus = findViewById(R.id.tv_status);
        tvFrames = findViewById(R.id.tv_frames);
        surfaceView = findViewById(R.id.surface_view);

        etRelayUrl.setText("wss://camara-relay.onrender.com");

        wsClient = new OkHttpClient();

        btnStart.setOnClickListener(v -> startStream());
        btnStop.setOnClickListener(v -> stopStream());

        checkPermissions();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
                return;
            }
        }
        openCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }

    private void openCamera() {
        try {
            int id = 0;
            camera = Camera.open(id);
        } catch (Exception e) {
            tvStatus.setText("Error: " + e.getMessage());
            return;
        }

        if (camera == null) {
            tvStatus.setText("Error: No se pudo abrir la camara");
            return;
        }

        if (surfaceCallback != null) {
            surfaceView.getHolder().removeCallback(surfaceCallback);
        }
        surfaceCallback = new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    cameraHelper.startPreview(holder);
                    Camera.Size size = camera.getParameters().getPreviewSize();
                    previewWidth = size.width;
                    previewHeight = size.height;
                    tvStatus.setText("Camara lista - Pulsa Iniciar");
                    btnStart.setEnabled(true);
                } catch (Exception e) {
                    tvStatus.setText("Error preview: " + e.getMessage());
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {}
        };
        surfaceView.getHolder().addCallback(surfaceCallback);
    }

    private void startStream() {
        String url = etRelayUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Introduce la URL del relay", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            url = "wss://" + url;
        }

        if (camera == null) {
            Toast.makeText(this, "Camara no disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        final String relayUrl = url;
        Request request = new Request.Builder().url(relayUrl).build();

        webSocket = wsClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                String msg = "{\"type\":\"register\",\"camera_id\":\"android\",\"name\":\"Camera " + Build.MODEL + "\"}";
                ws.send(msg);
                streaming = true;
                runOnUiThread(() -> {
                    btnStart.setEnabled(false);
                    btnStop.setEnabled(true);
                    tvStatus.setText("Transmitiendo...");
                    startFrameCapture();
                });
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                streaming = false;
                runOnUiThread(() -> {
                    tvStatus.setText("Error: " + t.getMessage());
                    btnStart.setEnabled(true);
                    btnStop.setEnabled(false);
                });
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                streaming = false;
                runOnUiThread(() -> {
                    tvStatus.setText("Desconectado");
                    btnStart.setEnabled(true);
                    btnStop.setEnabled(false);
                });
            }
        });
    }

    private void startFrameCapture() {
        final WebSocket ws = webSocket;
        final ByteArrayOutputStream jpegBuffer = new ByteArrayOutputStream();
        final Rect rect = new Rect(0, 0, previewWidth, previewHeight);

        camera.setPreviewCallback((data, cam) -> {
            if (!streaming || ws == null) return;

            try {
                YuvImage yuv = new YuvImage(data, ImageFormat.NV21, previewWidth, previewHeight, null);
                jpegBuffer.reset();
                yuv.compressToJpeg(rect, 70, jpegBuffer);
                byte[] jpeg = jpegBuffer.toByteArray();

                ws.send(ByteString.of(jpeg));

                frameCount++;
                runOnUiThread(() -> tvFrames.setText("Frames: " + frameCount));
            } catch (Exception e) {
            }
        });
    }

    private void stopStream() {
        streaming = false;
        if (webSocket != null) {
            webSocket.close(1000, "Stopped");
            webSocket = null;
        }
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
            } catch (Exception ignored) {}
        }
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        tvStatus.setText("Detenido");
        tvFrames.setText("Frames: 0");
        frameCount = 0;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (streaming) stopStream();
        if (cameraHelper != null) {
            cameraHelper.release();
            cameraHelper = null;
            camera = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!streaming) {
            openCamera();
        }
    }

    @Override
    protected void onDestroy() {
        if (webSocket != null) webSocket.close(1000, "Destroy");
        if (cameraHelper != null) cameraHelper.release();
        super.onDestroy();
    }
}
