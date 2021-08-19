package com.example.myaudiorecorder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    public static final String TAG = MainActivity.class.getSimpleName();

    private FileOutputStream fos;
    private DataOutputStream dos;
    private boolean isRecording;
    private AudioRecord record;
    private Thread recorderThread;
    private int bufferSize;
    private int sampleRate = 48000;
    private int channel = AudioFormat.CHANNEL_IN_STEREO;
    private int chanelNum = (channel == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1);
    private int dataConfig = AudioFormat.ENCODING_PCM_16BIT;
    private final String recordPcmFile = "/sdcard/recorder_test.pcm";
    private final String recordWavFile = "/sdcard/recorder_test.wav";

    private SurfaceView surfaceView;
    private MediaPlayer mediaPlayer;
    private static final String videoPath = "/sdcard/aaa.ts";

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
        initPlayer();
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
        surfaceView = findViewById(R.id.video);
        surfaceView.getHolder().addCallback(this);

        startRecorder = findViewById(R.id.start_recorder);
        startRecorder.setOnClickListener(mRecorderListener);
        stopRecorder = findViewById(R.id.stop_recorder);
        stopRecorder.setOnClickListener(mRecorderListener);
    }

    private void initPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setLooping(true);
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.start();
            }
        });
    }

    private void initRecorder() {
        //根据定义好的几个配置，来获取合适的缓冲大小
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channel, dataConfig);
        Log.i(TAG, "RecordTask: dataSize=" + bufferSize);
        //实例化AudioRecord
        record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, channel, dataConfig, bufferSize);
    }

    private void startRecorder() {
        Log.d(TAG, "start recorder");

        try {
            fos = new FileOutputStream(recordPcmFile, false);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //开通输出流到指定的文件,audioFile是保存的音频文件File对象
        dos = new DataOutputStream(new BufferedOutputStream(fos));

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
        convertWaveFile();
    }

    // 这里得到可播放的音频文件
    private void convertWaveFile() {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = 0;
        long longSampleRate = sampleRate;
        int channels = chanelNum;
        long byteRate = 16 * longSampleRate * channels / 8;
        byte[] data = new byte[bufferSize];
        try {
            in = new FileInputStream(recordPcmFile);
            out = new FileOutputStream(recordWavFile);
            totalAudioLen = in.getChannel().size();
            //由于不包括RIFF和WAV
            totalDataLen = totalAudioLen + 36;
            WriteWaveFileHeader(out, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate);
            while (in.read(data) != -1) {
                out.write(data);
            }
            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* 任何一种文件在头部添加相应的头文件才能够确定的表示这种文件的格式，wave是RIFF文件结构，每一部分为一个chunk，其中有RIFF WAVE chunk， FMT Chunk，Fact chunk,Data chunk,其中Fact chunk是可以选择的， */
    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen, long totalDataLen, long longSampleRate,
                                     int channels, long byteRate) throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);//数据大小
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';//WAVE
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        //FMT Chunk
        header[12] = 'f'; // 'fmt '
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';//过渡字节
        //数据大小
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        //编码方式 10H为PCM编码格式
        header[20] = 1; // format = 1
        header[21] = 0;
        //通道数
        header[22] = (byte) channels;
        header[23] = 0;
        //采样率，每个通道的播放速度
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        //音频数据传送速率,采样率*通道数*采样深度/8
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        // 确定系统一次要处理多少个这样字节的数据，确定缓冲区，通道数*采样位数
        header[32] = (byte) (1 * 16 / 8);
        header[33] = 0;
        //每个样本的数据位数
        header[34] = 16;
        header[35] = 0;
        //Data chunk
        header[36] = 'd';//data
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
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

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        if(mediaPlayer != null) {
            try {
                mediaPlayer.setDataSource(videoPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mediaPlayer.setDisplay(surfaceHolder);
            mediaPlayer.prepareAsync();
            //mediaPlayer.setVolume(0.1f, 0.1f);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if(mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.reset();
        }
    }

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
