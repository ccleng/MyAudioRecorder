package com.example.myaudiorecorder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = MainActivity.class.getSimpleName();

    private FileOutputStream fos;
    private DataOutputStream dos;
    private boolean isRecording;
    private AudioRecord record;
    private Thread recorderThread;
    private int bufferSize;
    private int channel = AudioFormat.CHANNEL_IN_STEREO;
    private int dataConfig = AudioFormat.ENCODING_PCM_16BIT;

    private Button startRecorder;
    private Button stopRecorder;

    private EventHandler mHandler;
    private static final int EVENT_START_RECORDER = 0;
    private static final int EVENT_STOP_RECORDER = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initRecorder();
        Looper looper;
        if((looper = Looper.myLooper()) != null) {
            mHandler = new EventHandler(looper);
        } else if((looper = Looper.getMainLooper()) != null) {
            mHandler = new EventHandler(looper);
        } else {
            mHandler = null;
        }

        verifyStoragePermissions(this);
    }

    private void initView() {
        startRecorder = findViewById(R.id.start_recorder);
        startRecorder.setOnClickListener(mRecorderListener);
        stopRecorder = findViewById(R.id.stop_recorder);
        stopRecorder.setOnClickListener(mRecorderListener);
    }

    private void initRecorder() {
        try {
            fos = new FileOutputStream("/sdcard/recorder_test.pcm", false);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //开通输出流到指定的文件,audioFile是保存的音频文件File对象
        dos = new DataOutputStream(new BufferedOutputStream(fos));
        //根据定义好的几个配置，来获取合适的缓冲大小
        bufferSize = AudioRecord.getMinBufferSize(48000, channel, dataConfig);
        Log.i(TAG, "RecordTask: dataSize=" + bufferSize);
        //实例化AudioRecord
        record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, 48000, channel, dataConfig, bufferSize);

    }

    private void startRecorder() {
        Log.d(TAG, "start recorder");
        //开始录制
        record.startRecording();

        //定义循环，根据isRecording的值来判断是否继续录制
        isRecording = true;
        recorderThread = new Thread(new Runnable() {
            byte audioData[] = new byte[bufferSize];
            @Override
            public void run() {
                while (isRecording) {
                    int number = record.read(audioData, 0, bufferSize);
                    try {
                        //Log.d(TAG, "recorder read data length:" + number);
                        dos.write(audioData);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        recorderThread.start();
    }

    private void stopRecorder() {
        Log.d(TAG, "stop recorder");
        isRecording = false;
        try {
            recorderThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //录制结束
        record.stop();
    }

    private View.OnClickListener mRecorderListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Message msg = Message.obtain();
            switch (view.getId()) {
                case R.id.start_recorder:
                    msg.what = EVENT_START_RECORDER;
                    mHandler.sendMessage(msg);
                    break;
                case R.id.stop_recorder:
                    msg.what = EVENT_STOP_RECORDER;
                    mHandler.sendMessage(msg);
                    break;
            }
        }
    };

    private class EventHandler extends Handler {
        public EventHandler(Looper looper) {super(looper);}

        public void handleMessage(Message msg) {
            switch(msg.what) {
                case EVENT_START_RECORDER:
                    startRecorder();
                    break;
                case EVENT_STOP_RECORDER:
                    stopRecorder();
                    break;
            }
        }
    }

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.RECORD_AUDIO"};

    /*动态申请权限*/
    private void verifyStoragePermissions(Activity activity) {

        try {
            //检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
