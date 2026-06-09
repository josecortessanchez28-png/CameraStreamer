package com.camerastreamer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private Button btnStart, btnStop;
    private TextView tvStatus, tvFrames;
    private ImageView ivPreview;
    private OkHttpClient client;
    private Handler handler = new Handler();
    private boolean streaming = false;
    private int frameCount = 0;
    private String relayUrl = "https://camara-relay.onrender.com/upload";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        tvStatus = findViewById(R.id.tv_status);
        tvFrames = findViewById(R.id.tv_frames);
        ivPreview = findViewById(R.id.iv_preview);

        client = new OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build();

        btnStart.setOnClickListener(v -> startStream());
        btnStop.setOnClickListener(v -> stopStream());
    }

    private void startStream() {
        streaming = true;
        frameCount = 0;
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        tvStatus.setText("Conectando...");
        captureAndSend();
    }

    private void captureAndSend() {
        if (!streaming) return;

        new Thread(() -> {
            try {
                Request shotReq = new Request.Builder()
                    .url("http://127.0.0.1:8080/shot.jpg")
                    .build();
                Response shotResp = client.newCall(shotReq).execute();
                byte[] jpeg = shotResp.body().bytes();
                shotResp.close();

                Bitmap bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                runOnUiThread(() -> {
                    ivPreview.setImageBitmap(bmp);
                    tvStatus.setText("Transmitiendo");
                });

                Request relayReq = new Request.Builder()
                    .url(relayUrl)
                    .post(RequestBody.create(jpeg, MediaType.parse("image/jpeg")))
                    .build();
                Response relayResp = client.newCall(relayReq).execute();
                relayResp.close();

                frameCount++;
                runOnUiThread(() -> tvFrames.setText("Frames: " + frameCount));
            } catch (IOException e) {
                runOnUiThread(() -> {
                    tvStatus.setText("Error: " + e.getMessage());
                    if (!streaming) {
                        ivPreview.setImageResource(android.R.color.transparent);
                    }
                });
            }

            if (streaming) {
                handler.postDelayed(this::captureAndSend, 200);
            }
        }).start();
    }

    private void stopStream() {
        streaming = false;
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        tvStatus.setText("Detenido");
    }
}
