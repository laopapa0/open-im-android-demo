package io.openim.android.ouiconversation.widget;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import io.openim.android.ouicore.utils.Constants;

/**
 * 语音录制工具类
 * 使用 MediaRecorder 实现录音功能
 */
public class VoiceRecorder {
    private static final String TAG = "VoiceRecorder";
    private static final int MIN_RECORD_DURATION = 1000; // 最小录音时长 1秒
    
    private MediaRecorder mediaRecorder;
    private String outputFilePath;
    private long startTime;
    private boolean isRecording = false;
    private Context context;
    private Handler mainHandler;
    
    // 录音状态回调
    public interface RecordListener {
        void onStart();
        void onProgress(int seconds);
        void onComplete(String filePath, int duration);
        void onError(String error);
        void onCancel();
    }
    
    private RecordListener listener;
    
    public VoiceRecorder(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setRecordListener(RecordListener listener) {
        this.listener = listener;
    }
    
    /**
     * 开始录音
     */
    public void startRecording() {
        if (isRecording) {
            Log.w(TAG, "已经在录音中");
            return;
        }
        
        // 检查权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            if (listener != null) {
                listener.onError("没有录音权限");
            }
            return;
        }
        
        try {
            // 创建录音文件 - 使用 Constants.AUDIO_DIR
            File voiceDir = new File(Constants.AUDIO_DIR);
            if (!voiceDir.exists()) {
                voiceDir.mkdirs();
            }
            
            String fileName = "voice_" + UUID.randomUUID().toString() + ".aac";
            outputFilePath = new File(voiceDir, fileName).getAbsolutePath();
            
            // 初始化 MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(outputFilePath);
            
            mediaRecorder.prepare();
            mediaRecorder.start();
            
            startTime = System.currentTimeMillis();
            isRecording = true;
            
            if (listener != null) {
                listener.onStart();
            }
            
            // 开始进度更新
            startProgressUpdate();
            
        } catch (IOException e) {
            Log.e(TAG, "录音启动失败", e);
            if (listener != null) {
                listener.onError("录音启动失败: " + e.getMessage());
            }
            reset();
        }
    }
    
    /**
     * 停止录音并保存
     */
    public void stopRecording() {
        if (!isRecording) {
            return;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            
            isRecording = false;
            
            // 检查录音时长
            if (duration < MIN_RECORD_DURATION) {
                // 录音太短，删除文件
                deleteCurrentFile();
                if (listener != null) {
                    listener.onError("录音时间太短");
                }
            } else {
                int seconds = (int) (duration / 1000);
                if (listener != null) {
                    listener.onComplete(outputFilePath, seconds);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "停止录音失败", e);
            if (listener != null) {
                listener.onError("停止录音失败: " + e.getMessage());
            }
            reset();
        }
    }
    
    /**
     * 取消录音
     */
    public void cancelRecording() {
        if (!isRecording) {
            return;
        }
        
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            
            isRecording = false;
            
            // 删除录音文件
            deleteCurrentFile();
            
            if (listener != null) {
                listener.onCancel();
            }
        } catch (Exception e) {
            Log.e(TAG, "取消录音失败", e);
            reset();
        }
    }
    
    /**
     * 是否正在录音
     */
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * 获取当前录音时长（秒）
     */
    public int getCurrentDuration() {
        if (!isRecording) {
            return 0;
        }
        return (int) ((System.currentTimeMillis() - startTime) / 1000);
    }
    
    private void startProgressUpdate() {
        if (!isRecording) {
            return;
        }
        
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRecording && listener != null) {
                    int seconds = getCurrentDuration();
                    listener.onProgress(seconds);
                    mainHandler.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }
    
    private void deleteCurrentFile() {
        if (outputFilePath != null) {
            File file = new File(outputFilePath);
            if (file.exists()) {
                file.delete();
            }
        }
    }
    
    private void reset() {
        isRecording = false;
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception ignored) {
            }
            mediaRecorder = null;
        }
        deleteCurrentFile();
    }
    
    public void release() {
        reset();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
