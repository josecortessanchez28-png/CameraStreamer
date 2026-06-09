package com.camerastreamer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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

public class MainActivity extends AppCompatActivity {

    private CameraHelper cameraHelper;
    private Camera camera;
    private SurfaceView surfaceView;
    private EditText etRelayUrl;
    private Button btnStart;
    private Button btnStop;
    private TextView tvStatus;
    private TextView tvFrames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surface_view);
        etRelayUrl = findViewById(R.id.et_relay_url);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        tvStatus = findViewById(R.id.tv_status);
        tvFrames = findViewById(R.id.tv_frames);

        etRelayUrl.setText("wss://confidential-gibson-drawing-flower.trycloudflare.com");

        tvStatus.setText("Iniciando...");

        StreamService.staticCallback = new StreamService.StreamCallback() {
            @Override
            public void onFrameCount(int count) {
                runOnUiThread(() -> tvFrames.setText("Frames: " + count));
            }

            @Override
            public void onStatus(String status) {
                runOnUiThread(() -> tvStatus.setText(status));
            }
        };

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
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                tvStatus.setText("Permiso de cámara denegado");
            }
        }
    }

    private void openCamera() {
        cameraHelper = new CameraHelper();
        int cameraId = CameraHelper.findBackCamera();
        camera = cameraHelper.open(cameraId);

        if (camera == null) {
            tvStatus.setText("Error: No se pudo abrir la cámara");
            btnStart.setEnabled(false);
            return;
        }

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                cameraHelper.startPreview(holder);
                btnStart.setEnabled(true);
                tvStatus.setText("Cámara lista - Pulsa Iniciar");
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {}
        });
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

        Intent intent = new Intent(this, StreamService.class);
        intent.putExtra("relay_url", url);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        tvStatus.setText("Iniciando...");
    }

    private void stopStream() {
        Intent intent = new Intent(this, StreamService.class);
        stopService(intent);

        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        tvStatus.setText("Detenido");
        tvFrames.setText("Frames: 0");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraHelper != null) cameraHelper.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraHelper != null) {
            int cameraId = CameraHelper.findBackCamera();
            camera = cameraHelper.open(cameraId);
            if (camera != null && surfaceView.getHolder().getSurface().isValid()) {
                cameraHelper.startPreview(surfaceView.getHolder());
                btnStart.setEnabled(true);
                tvStatus.setText("Cámara lista - Pulsa Iniciar");
            }
        }
    }

    @Override
    protected void onDestroy() {
        StreamService.staticCallback = null;
        super.onDestroy();
    }
}
