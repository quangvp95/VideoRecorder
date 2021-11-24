package com.junerver.videorecorder.pip;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.junerver.videorecorder.R;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

public class PipView extends FrameLayout {
    private static final String TAG = "QuangNHe";
    private static final int CAMERA_INFO = Camera.CameraInfo.CAMERA_FACING_BACK;

    private final WindowManager.LayoutParams windowParams;
    private final Handler handler = new Handler();
    private WindowManager mWindowManager;
    private MediaRecorder mRecorder;
    private boolean cameraReleaseEnable = true; //回收摄像头
    private boolean recorderReleaseEnable = false; //回收recorder
    private boolean mStartedFlag = false; //录像中标志

    private String dirPath; //目标文件夹地址
    private String path; //最终视频路径

    private Camera mCamera;

    public PipView(@NonNull Context context) {
        this(context, null);
    }

    public PipView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PipView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.pip_player, this);
        setBackgroundColor(Color.BLACK);

        windowParams = new WindowManager.LayoutParams();
        windowParams.height = 1;
        windowParams.width = 1;
        windowParams.format = PixelFormat.OPAQUE;
        windowParams.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            windowParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        SurfaceView mRecordView = findViewById(R.id.mSurfaceView);
        SurfaceHolder recordViewHolder = mRecordView.getHolder();
        recordViewHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (holder == null) return;
                Log.d(TAG, "surfaceChanged " + format + " | " + width + " | " + height);
                mCamera.startPreview();
                mCamera.cancelAutoFocus();
                mCamera.unlock();
                cameraReleaseEnable = true;
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "surfaceDestroyed " + holder);
            }

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (holder == null) return;
                Log.d(TAG, "surfaceCreated " + holder);
                try {
                    //Use the rear camera
                    mCamera = Camera.open(CAMERA_INFO);
                    mCamera.setDisplayOrientation(90);//旋转90度
                    mCamera.setPreviewDisplay(holder);
                    mCamera.enableShutterSound(false);
                    Camera.Parameters params = mCamera.getParameters();
                    //Note that here you need to obtain the optimal pixels according to the camera，
                    // If not set, the minimum 160x120 resolution will be configured according to the system default
                    Pair<Integer, Integer> size = getPreviewSize();
                    params.setPictureSize(size.first, size.second);
                    params.setJpegQuality(100);
                    params.setPictureFormat(ImageFormat.JPEG);
                    if (CAMERA_INFO == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);//1 Continuous focus
                    }
                    mCamera.setParameters(params);
                } catch (RuntimeException | IOException e) {
                    //Camera.open() may throw a RuntimeException when the camera service cannot be connected
                    e.printStackTrace();

                }
            }

        });

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startRecord();
            }
        }, 2000);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopRecord();
            }
        }, 10000);
    }

    //Start recording
    private void startRecord() {
        if (!mStartedFlag) {
            mStartedFlag = true;
            //start the timer
            recorderReleaseEnable = true;
            mRecorder = new MediaRecorder();
            mRecorder.reset();
            mRecorder.setCamera(mCamera);
            // Set audio source and video source These two items need to be placed before setOutputFormat
            mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            //Set output format
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            //These two items need to be placed after setOutputFormat, IOS must use ACC
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);  //音频编码格式
            //Appears when you stop recording on Huawei P20 pro using MPEG_4_SP format
            //MediaRecorder: stop failed: -1007
            //java.lang.RuntimeException: stop failed.
            // at android.media.MediaRecorder.stop(Native Method)
            mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264); //视频编码格式
            //Set the final output resolution
//            mRecorder.setVideoSize(640, 480);
            mRecorder.setVideoFrameRate(30);
            mRecorder.setVideoEncodingBitRate(3 * 1024 * 1024);
            mRecorder.setOrientationHint(90);
            //Set the maximum duration of the recording session (milliseconds)
            mRecorder.setMaxDuration(30 * 1000);
            path = Environment.getExternalStorageDirectory().getPath() + File.separator + "VideoRecorder";
            File dir = new File(path);
            if (!dir.exists()) {
                boolean mkdir = dir.mkdir();
                if (!mkdir)
                    Log.e(TAG, "startRecord mkdir ERR");
            }
            dirPath = dir.getAbsolutePath();
            path = dir.getAbsolutePath() + "/" + getDate() + ".mp4";
            Log.d(TAG, "startRecord path：" + path);
            mRecorder.setOutputFile(path);
            try {
                mRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mRecorder.start();
            //Start time in milliseconds
            long startTime = System.currentTimeMillis();  //Record start shooting time
            Log.e(TAG, "startRecord startTime: " + startTime);
        }
    }

    //End recording
    private void stopRecord() {

        if (mStartedFlag) {
            mStartedFlag = false;
            //End time in milliseconds
            long stopTime = System.currentTimeMillis();
            Log.e(TAG, "stopRecord stopTime: " + stopTime);
//          Method 1: Delay to ensure that the recording time is greater than 1s
//            if (stopTime-startTime<1100) {
//                Thread.sleep(1100+startTime-stopTime)
//            }
//            mRecorder.stop()
//            mRecorder.reset()
//            mRecorder.release()
//            recorderReleaseEnable = false
//            mCamera.lock()
//            mCamera.stopPreview()
//            mCamera.release()
//            cameraReleaseEnable = false
//            mBtnPlay.visibility = View.VISIBLE
//            MediaUtils.getImageForVideo(path) {
//                //获取到第一帧图片后再显示操作按钮
//                Log.d(TAG,"获取到了第一帧")
//                imgPath=it.absolutePath
//                mLlRecordOp.visibility = View.VISIBLE
//            }


//          Method 2: Change to capture the abnormality and take a photo
            try {
                mRecorder.stop();
                mRecorder.reset();
                mRecorder.release();
                recorderReleaseEnable = false;
                mCamera.lock();
                mCamera.stopPreview();
                mCamera.release();
                cameraReleaseEnable = false;
            } catch (java.lang.RuntimeException e) {
                //When catch to RuntimeException, it means that the recording time is too short,
                // at this time it will be changed from recording to shooting
                Log.e(TAG, "Shooting time is too short: " + e.getMessage());
                mRecorder.reset();
                mRecorder.release();
                recorderReleaseEnable = false;
            }
            removeFromWindow();
        }
    }

    //Take the 'preview size' supported by the camera from the bottom, and the difference between the
    // screen resolution and the screen resolution, the smallest diff is the best preview resolution
    private Pair<Integer, Integer> getPreviewSize() {
        int bestPreviewWidth = 1920;
        int bestPreviewHeight = 1080;
        int mCameraPreviewWidth;
        int mCameraPreviewHeight;
        int diffs = Integer.MAX_VALUE;
        WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point screenResolution = new Point();
        display.getSize(screenResolution);
        List<Camera.Size> availablePreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
        Log.e(TAG, "Screen width " + screenResolution.x + "  Screen height " + screenResolution.y);
        for (Camera.Size previewSize : availablePreviewSizes) {
            Log.v(TAG, " PreviewSizes = " + previewSize);
            mCameraPreviewWidth = previewSize.width;
            mCameraPreviewHeight = previewSize.height;
            int newDiffs = Math.abs(mCameraPreviewWidth - screenResolution.y) + Math.abs(mCameraPreviewHeight - screenResolution.x);
            Log.v(TAG, "newDiffs = " + newDiffs);
            if (newDiffs == 0) {
                bestPreviewWidth = mCameraPreviewWidth;
                bestPreviewHeight = mCameraPreviewHeight;
                break;
            }
            if (diffs > newDiffs) {
                bestPreviewWidth = mCameraPreviewWidth;
                bestPreviewHeight = mCameraPreviewHeight;
                diffs = newDiffs;
            }
            Log.e(TAG, previewSize.width + " | " + previewSize.height + " | Width " + bestPreviewWidth + " | height " + bestPreviewHeight);

        }
        Log.e(TAG, "Best width " + bestPreviewWidth + " best height " + bestPreviewHeight);
        return new Pair<>(bestPreviewWidth, bestPreviewHeight);
    }

    String getDate() {
        Calendar ca = Calendar.getInstance();
        int year = ca.get(Calendar.YEAR);         // 获取年份
        int month = ca.get(Calendar.MONTH);    // 获取月份
        int day = ca.get(Calendar.DATE);   // 获取日
        int minute = ca.get(Calendar.MINUTE);    // 分
        int hour = ca.get(Calendar.HOUR);    // 小时
        int second = ca.get(Calendar.SECOND);   // 秒
        return "" + year + (month + 1) + day + hour + minute + second;
    }

    public void addToWindow(WindowManager windowManager) {
        mWindowManager = windowManager;
        try {
            windowManager.addView(this, windowParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeFromWindow() {
        try {
            if (mWindowManager != null) {
                mWindowManager.removeView(this);
                mWindowManager = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
