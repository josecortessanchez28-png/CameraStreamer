package com.camerastreamer;

import android.hardware.Camera;

public class CameraHelper {

    private Camera camera;

    public static int findBackCamera() {
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return i;
            }
        }
        return -1;
    }

    public Camera open(int cameraId) {
        try {
            camera = Camera.open(cameraId);
            Camera.Parameters params = camera.getParameters();

            Camera.Size bestSize = null;
            for (Camera.Size size : params.getSupportedPreviewSizes()) {
                if (size.width <= 640 && size.height <= 480) {
                    if (bestSize == null || (size.width > bestSize.width && size.height > bestSize.height)) {
                        bestSize = size;
                    }
                }
            }
            if (bestSize != null) {
                params.setPreviewSize(bestSize.width, bestSize.height);
            }

            if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }

            camera.setParameters(params);
            return camera;
        } catch (Exception e) {
            return null;
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
            } catch (Exception ignored) {}
            camera = null;
        }
    }

    public Camera getCamera() {
        return camera;
    }
}
