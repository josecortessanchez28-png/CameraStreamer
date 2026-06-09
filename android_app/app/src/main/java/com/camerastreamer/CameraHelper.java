package com.camerastreamer;

import android.hardware.Camera;
import android.view.SurfaceHolder;

import java.io.IOException;

public class CameraHelper {

    private Camera camera;

    public Camera open(int cameraId) {
        try {
            camera = Camera.open(cameraId);
            return camera;
        } catch (Exception e) {
            return null;
        }
    }

    public void startPreview(SurfaceHolder holder) {
        if (camera == null) return;
        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setPreviewCallback(Camera.PreviewCallback callback) {
        if (camera != null) {
            camera.setPreviewCallback(callback);
        }
    }

    public void addCallbackBuffer(byte[] buffer) {
        if (camera != null) {
            camera.addCallbackBuffer(buffer);
        }
    }

    public void release() {
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            camera = null;
        }
    }

    public Camera getCamera() {
        return camera;
    }

    public static int findBackCamera() {
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return i;
            }
        }
        return 0;
    }
}
