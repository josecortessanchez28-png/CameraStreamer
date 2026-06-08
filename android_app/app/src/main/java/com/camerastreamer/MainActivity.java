package com.camerastreamer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText etRelayUrl;
    private Button btnStart;
    private Button btnStop;
    private TextView tvStatus;
    private TextView tvFrames;
    private boolean isStreaming = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etRelayUrl = findViewById(R.id.et_relay_url);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        tvStatus = findViewById(R.id.tv_status);
        tvFrames = findViewById(R.id.tv_frames);

        etRelayUrl.setText("wss://confidential-gibson-drawing-flower.trycloudflare.com");

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
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso de cámara concedido", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_LONG).show();
                finish();
            }
        }
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

        isStreaming = true;
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        tvStatus.setText("Iniciando...");
    }

    private void stopStream() {
        Intent intent = new Intent(this, StreamService.class);
        stopService(intent);

        isStreaming = false;
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        tvStatus.setText("Detenido");
        tvFrames.setText("Frames: 0");
    }

    @Override
    protected void onDestroy() {
        StreamService.staticCallback = null;
        super.onDestroy();
    }
}
