package com.optoma.newtetris;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.unity3d.player.*;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.IOException;
import java.util.Locale;

public class UnityPlayerActivity extends Activity {
    protected UnityPlayer mUnityPlayer; // don't change the name of this variable; referenced from native code
    public static final int CAMERA_PERM = 0;

    // Setup activity layout
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        getWindow().setFormat(PixelFormat.RGBX_8888); // <--- This makes xperia play happy

        mUnityPlayer = new UnityPlayer(this);
        setContentView(mUnityPlayer);
        mUnityPlayer.requestFocus();

        // face detection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                createFaceDetector();
            } else {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERM);
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != CAMERA_PERM) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            createFaceDetector();
            return;
        }

    }

    private CameraSource mCameraSource;

    private void createFaceDetector() {
        FaceDetector detector = new FaceDetector.Builder(getApplicationContext())
                // .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setTrackingEnabled(true)
                // .setProminentFaceOnly(true)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<Face>(new GraphicFaceTrackerFactory())
                        .build());

        mCameraSource = new CameraSource.Builder(getApplicationContext(), detector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedFps(30.0f)
                .build();

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mCameraSource.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static class GraphicFaceTrackerFactory
            implements MultiProcessor.Factory<Face> {

        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(face.getId());
        }
    }

    public static float minRotation = Float.MAX_VALUE;
    public static float maxRotation = Float.MIN_VALUE;

    public static float minX = Float.MAX_VALUE;
    public static float maxX = Float.MIN_VALUE;

    public static float minY = Float.MAX_VALUE;
    public static float maxY = Float.MIN_VALUE;


    private static class GraphicFaceTracker extends Tracker<Face> {
        // other stuff
        private final int faceId;

        public GraphicFaceTracker(int initialFaceId) {
            faceId = initialFaceId;
            Log.d("ken", "got new tracker: "+initialFaceId);
        }

        @Override
        public void onNewItem(int faceId, Face face) {

            Log.d("ken", "got new face: "+faceId);
            UnityPlayer.UnitySendMessage("Controller", "FaceEnter", getFaceParams(face));
        }

        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults,
                             Face face) {
            UnityPlayer.UnitySendMessage("Controller", "FaceMove", getFaceParams(face));
        }

        private String getFaceParams(Face face) {
            final PointF pos = face.getPosition();
            final float w = face.getWidth();
            final float h = face.getHeight();

            float normX = (pos.x + w/2) / 640;
            float normY = (pos.y + h/2) / 480;

            if(normX < 0) normX = 0; if(normX > 1) normX = 1;
            if(normY < 0) normY = 0; if(normY > 1) normY = 1;

            // Log.d("ken", String.format("face %d (z: %f, pos: (%f, %f), w: %f, h: %f)", face.getId(), face.getEulerZ(), pos.x + w/2, pos.y + h/2, w, h));
            // Log.d("ken", String.format("sending %f, %f, %f", normX, normY, face.getEulerZ()));

            return String.format(Locale.US, "%d;%f;%f;%f", face.getId(), normX, normY, face.getEulerZ());
        }

        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            Log.d("ken", "face missing: "+faceId);
        }

        @Override
        public void onDone() {
            Log.d("ken", "face gone: "+faceId);
            UnityPlayer.UnitySendMessage("Controller", "FaceExit", String.valueOf(faceId));

            // Log.d("ken", String.format("x: %f ~ %f, y: %f ~ %f, rot: %f ~ %f", minX, maxX, minY, maxY, minRotation, maxRotation));
        }
    }

    @Override protected void onNewIntent(Intent intent)
    {
        // To support deep linking, we need to make sure that the client can get access to
        // the last sent intent. The clients access this through a JNI api that allows them
        // to get the intent set on launch. To update that after launch we have to manually
        // replace the intent with the one caught here.
        setIntent(intent);
    }

    // Quit Unity
    @Override protected void onDestroy ()
    {
        mUnityPlayer.quit();

        if (mCameraSource != null) {
            mCameraSource.release();
        }

        super.onDestroy();
    }

    // Pause Unity
    @Override protected void onPause()
    {
        super.onPause();

        if(mCameraSource != null) {
            mCameraSource.stop();
        }

        mUnityPlayer.pause();
    }

    // Resume Unity
    @Override protected void onResume()
    {
        super.onResume();

        if(mCameraSource != null) {
            try {
                mCameraSource.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        mUnityPlayer.resume();
    }

    // Low Memory Unity
    @Override public void onLowMemory()
    {
        super.onLowMemory();
        mUnityPlayer.lowMemory();
    }

    // Trim Memory Unity
    @Override public void onTrimMemory(int level)
    {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_RUNNING_CRITICAL)
        {
            mUnityPlayer.lowMemory();
        }
    }

    // This ensures the layout will be correct.
    @Override public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        mUnityPlayer.configurationChanged(newConfig);
    }

    // Notify Unity of the focus change.
    @Override public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        mUnityPlayer.windowFocusChanged(hasFocus);
    }

    // For some reason the multiple keyevent type is not supported by the ndk.
    // Force event injection by overriding dispatchKeyEvent().
    @Override public boolean dispatchKeyEvent(KeyEvent event)
    {
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE)
            return mUnityPlayer.injectEvent(event);
        return super.dispatchKeyEvent(event);
    }

    // Pass any events not handled by (unfocused) views straight to UnityPlayer
    @Override public boolean onKeyUp(int keyCode, KeyEvent event)     { return mUnityPlayer.injectEvent(event); }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event)   { return mUnityPlayer.injectEvent(event); }
    @Override public boolean onTouchEvent(MotionEvent event)          { return mUnityPlayer.injectEvent(event); }
    /*API12*/ public boolean onGenericMotionEvent(MotionEvent event)  { return mUnityPlayer.injectEvent(event); }
}
