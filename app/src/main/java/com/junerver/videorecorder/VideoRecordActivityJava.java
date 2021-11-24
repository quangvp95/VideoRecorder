package com.junerver.videorecorder;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

@SuppressWarnings({"deprecation", "ConstantConditions"})
public class VideoRecordActivityJava extends AppCompatActivity {
    public static final String TAG = "QuangNHe";

    private static final int CAMERA_INFO = Camera.CameraInfo.CAMERA_FACING_FRONT;

    private static final int TYPE_VIDEO = 0;
    private static final int TYPE_IMAGE = 1;
    private final int maxSec = 10; //视频总时长
    private final Handler handler = new Handler();
    private boolean mStartedFlag = false; //录像中标志
    private boolean mPlayFlag = false;
    private MediaRecorder mRecorder;
    private SurfaceHolder mReplayHolder;
    private Camera mCamera;
    private MediaPlayer mMediaPlayer;
    private String dirPath; //目标文件夹地址
    private String path; //最终视频路径
    private String imgPath; //缩略图 或 拍照模式图片位置
    private int timer = 0;//计时器
    private boolean cameraReleaseEnable = true; //回收摄像头
    private boolean recorderReleaseEnable = false; //回收recorder
    private boolean playerReleaseEnable = false; //回收palyer

    private int mType = TYPE_VIDEO; //默认为视频模式
    private Button mBtnRecord;
    private Button mBtnPlay;
    private LinearLayout mLlRecordBtn;
    private LinearLayout mLlRecordOp;
    private MaterialProgressBar mProgress;
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            timer++;
//            Log.d("计数器","$timer")
            if (timer < 100) {
                // 之所以这里是100 是为了方便使用进度条
                mProgress.setProgress(timer);
                //之所以每一百毫秒增加一次计时器是因为：总时长的毫秒数 / 100 即每次间隔延时的毫秒数 为 100
                handler.postDelayed(this, maxSec * 10L);
            } else {
                //停止录制 保存录制的流、显示供操作的ui
                Log.d("到最大拍摄时间", "");
                stopRecord();
                System.currentTimeMillis();
            }

        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_record_java);
        mMediaPlayer = new MediaPlayer();

        mBtnRecord = findViewById(R.id.mBtnRecord);
        mBtnPlay = findViewById(R.id.mBtnPlay);
        Button mBtnCancel = findViewById(R.id.mBtnCancel);
        Button mBtnSubmit = findViewById(R.id.mBtnSubmit);
        mLlRecordBtn = findViewById(R.id.mLlRecordBtn);
        mLlRecordOp = findViewById(R.id.mLlRecordOp);
        mProgress = findViewById(R.id.mProgress);

        SurfaceView mRecordView = findViewById(R.id.mRecordView);
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
                handler.removeCallbacks(runnable);
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
                    params.setPictureFormat(PixelFormat.JPEG);
                    if (CAMERA_INFO == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);//1 Continuous focus
                    }
                    mCamera.setParameters(params);
                } catch (RuntimeException | IOException e) {
                    //Camera.open() may throw a RuntimeException when the camera service cannot be connected
                    e.printStackTrace();
                    finish();
                }
            }

        });

        SurfaceView mReplayView = findViewById(R.id.mReplayView);
        SurfaceHolder replayViewHolder = mReplayView.getHolder();
        replayViewHolder.addCallback(new SurfaceHolder.Callback() {

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mReplayHolder = holder;
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                mReplayHolder = holder;
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        });

        mBtnRecord.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Log.d(TAG, "Touch the screen: " + event.getAction());
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startRecord();
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    stopRecord();
                }
                return true;
            }
        });
        mBtnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playRecord();
            }
        });
        mBtnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mType == TYPE_VIDEO) {
                    stopPlay();
                    File videoFile = new File(path);
                    if (videoFile.exists() && videoFile.isFile()) {
                        boolean delete = videoFile.delete();
                        if (!delete)
                            Log.e(TAG, "mBtnCancel onClick deleteVideo ERR");
                    }
                } else {
                    //拍照模式
                    File imgFile = new File(imgPath);
                    if (imgFile.exists() && imgFile.isFile()) {
                        boolean delete = imgFile.delete();
                        if (!delete)
                            Log.e(TAG, "mBtnCancel onClick deleteElse ERR");
                    }
                }
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
        mBtnSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPlay();
                Intent intent = new Intent();
                intent.putExtra("path", path);
                intent.putExtra("imagePath", imgPath);
                intent.putExtra("type", mType);
                if (mType == TYPE_IMAGE) {
                    //删除一开始创建的视频文件
                    File videoFile = new File(path);
                    if (videoFile.exists() && videoFile.isFile()) {
                        boolean delete = videoFile.delete();
                        if (!delete)
                            Log.e(TAG, "mBtnSubmit onClick deleteImage ERR");
                    }
                }
                setResult(Activity.RESULT_OK, intent);
                finish();

            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mPlayFlag) {
            stopPlay();
        }
        if (mStartedFlag) {
            Log.d(TAG, "onStop");
            stopRecord();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recorderReleaseEnable) mRecorder.release();
        if (cameraReleaseEnable) {
            mCamera.stopPreview();
            mCamera.release();
        }
        if (playerReleaseEnable) {
            mMediaPlayer.release();
        }
    }

    //Start recording
    private void startRecord() {
        timer = 0;
        if (!mStartedFlag) {
            mStartedFlag = true;
            mLlRecordOp.setVisibility(View.INVISIBLE);
            mBtnPlay.setVisibility(View.INVISIBLE);
            mLlRecordBtn.setVisibility(View.VISIBLE);
            mProgress.setVisibility(View.VISIBLE); //The progress bar is visible
            //start the timer
            handler.postDelayed(runnable, maxSec * 10L);
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
            mBtnRecord.setEnabled(false);
            mBtnRecord.setClickable(false);

            mLlRecordBtn.setVisibility(View.INVISIBLE);
            mProgress.setVisibility(View.INVISIBLE);

            handler.removeCallbacks(runnable);
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
                mBtnPlay.setVisibility(View.VISIBLE);
                MediaUtils.getImageForVideo(path, new MediaUtils.OnLoadVideoImageListener() {
                    @Override
                    public void onLoadImage(File file) {
                        //Display the operation button after the first frame of picture is obtained
                        Log.d(TAG, "Got the first frame");
                        imgPath = file.getAbsolutePath();
                        mLlRecordOp.setVisibility(View.VISIBLE);
                    }
                });
            } catch (java.lang.RuntimeException e) {
                //When catch to RuntimeException, it means that the recording time is too short,
                // at this time it will be changed from recording to shooting
                mType = TYPE_IMAGE;
                Log.e(TAG, "Shooting time is too short: " + e.getMessage());
                mRecorder.reset();
                mRecorder.release();
                recorderReleaseEnable = false;
                mCamera.takePicture(null, null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        saveImage(data, new OnDoneListener() {
                            @Override
                            public void onDone(String imagePath) {
                                Log.d(TAG, "Switch to taking pictures and get picture data " + imagePath);
                                imgPath = imagePath;
                                mCamera.lock();
                                mCamera.stopPreview();
                                mCamera.release();
                                cameraReleaseEnable = false;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mBtnPlay.setVisibility(View.INVISIBLE);
                                        mLlRecordOp.setVisibility(View.VISIBLE);
                                    }
                                });

                            }
                        });

                    }
                });
            }
        }
    }

    //Play video
    private void playRecord() {
        // Fix the problem that the home button cannot be played
        // when the home button is cut out and cut back again during recording
        if (cameraReleaseEnable) {
            Log.d(TAG, "Recycle camera resources");
            mCamera.lock();
            mCamera.stopPreview();
            mCamera.release();
            cameraReleaseEnable = false;
        }
        playerReleaseEnable = true;
        mPlayFlag = true;
        mBtnPlay.setVisibility(View.INVISIBLE);

        mMediaPlayer.reset();
        Uri uri = Uri.parse(path);
        mMediaPlayer = MediaPlayer.create(VideoRecordActivityJava.this, uri);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setDisplay(mReplayHolder);
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mBtnPlay.setVisibility(View.VISIBLE);
            }
        });
        try {
            mMediaPlayer.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mMediaPlayer.start();
    }

    //Stop playing video
    private void stopPlay() {
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
        }
    }

    /**
     * 获取系统时间
     */
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

    /**
     * @param data     The byte array obtained from the camera photo callback
     * @param listener callback function after saving the picture
     * @Author Junerver
     * Created at 2019/5/23 15:13
     */
    void saveImage(final byte[] data, final OnDoneListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //Method 1: java nio save the picture, the picture after saving has a 0 degree rotation angle
//            val imgFileName = "IMG_" + getDate() + ".jpg"
//            val imgFile = File(dirPath + File.separator + imgFileName)
//            val outputStream = FileOutputStream(imgFile)
//            val fileChannel = outputStream.channel
//            val buffer = ByteBuffer.allocate(data.size)
//            try {
//                buffer.put(data)
//                buffer.flip()
//                fileChannel.write(buffer)
//            } catch (e: IOException) {
//                Log.e("写图片失败", e.message)
//            } finally {
//                try {
//                    outputStream.close()
//                    fileChannel.close()
//                    buffer.clear()
//                } catch (e: IOException) {
//                    Log.e("关闭图片失败", e.message)
//                }
//            }

                //Method 2: Bitmap save Rotate the shooting result by 90 degrees
                String imgFileName = "IMG_" + getDate() + ".jpg";
                File imgFile = new File(dirPath + File.separator + imgFileName);
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                Bitmap newBitmap = PictureUtils.rotateBitmap(bitmap, 90);
                try {
                    boolean newFile = imgFile.createNewFile();
                    if (!newFile)
                        throw new IOException(" named file already exists");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                BufferedOutputStream os;
                try {
                    os = new BufferedOutputStream(new FileOutputStream(imgFile));
                    newBitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
                    os.flush();
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int degree = PictureUtils.getBitmapDegree(imgFile.getAbsolutePath());
                Log.d(TAG, "The picture angle is：" + degree);
                listener.onDone(imgFile.getAbsolutePath());

            }
        }).start();
    }

    //Take the 'preview size' supported by the camera from the bottom, and the difference between the
    // screen resolution and the screen resolution, the smallest diff is the best preview resolution
    private Pair<Integer, Integer> getPreviewSize() {
        int bestPreviewWidth = 1920;
        int bestPreviewHeight = 1080;
        int mCameraPreviewWidth;
        int mCameraPreviewHeight;
        int diffs = Integer.MAX_VALUE;
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
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

//    private void takePhoto() {
//
//        System.out.println("Preparing to take photo");
//        Camera camera = null;
//
//        int cameraCount = 0;
//        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//        cameraCount = Camera.getNumberOfCameras();
//        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
//            SystemClock.sleep(1000);
//
//            Camera.getCameraInfo(camIdx, cameraInfo);
//
//            try {
//                camera = Camera.open(camIdx);
//            } catch (RuntimeException e) {
//                System.out.println("Camera not available: " + camIdx);
//                camera = null;
//                //e.printStackTrace();
//            }
//            try {
//                if (null == camera) {
//                    System.out.println("Could not get camera instance");
//                } else {
//                    System.out.println("Got the camera, creating the dummy surface texture");
//                    //SurfaceTexture dummySurfaceTextureF = new SurfaceTexture(0);
//                    try {
//                        //camera.setPreviewTexture(dummySurfaceTextureF);
//                        camera.setPreviewTexture(new SurfaceTexture(0));
//                        camera.startPreview();
//                    } catch (Exception e) {
//                        System.out.println("Could not set the surface preview texture");
//                        e.printStackTrace();
//                    }
//                    camera.takePicture(null, null, new Camera.PictureCallback() {
//
//                        @Override
//                        public void onPictureTaken(byte[] data, Camera camera) {
//                            File pictureFileDir = getDir();
//                            if (!pictureFileDir.exists() && !pictureFileDir.mkdirs()) {
//                                return;
//                            }
//                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyymmddhhmmss");
//                            String date = dateFormat.format(new Date());
//                            String photoFile = "PictureFront_" + "_" + date + ".jpg";
//                            String filename = pictureFileDir.getPath() + File.separator + photoFile;
//                            File mainPicture = new File(filename);
////                            addImageFile(mainPicture);
//
//                            try {
//                                FileOutputStream fos = new FileOutputStream(mainPicture);
//                                fos.write(data);
//                                fos.close();
//                                System.out.println("image saved");
//                            } catch (Exception error) {
//                                System.out.println("Image could not be saved");
//                            }
//                            camera.release();
//                        }
//                    });
//                }
//            } catch (Exception e) {
//                camera.release();
//            }
//
//
//        }
//    }

    private interface OnDoneListener {
        void onDone(String path);
    }

}
