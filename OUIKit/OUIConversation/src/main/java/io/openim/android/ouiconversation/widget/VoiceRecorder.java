package io.openim.android.ouiconversation.widget;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.util.UUID;

import io.openim.android.ouicore.utils.Constants;

/**
 * 语音录制工具类
 */
public class VoiceRecorder {
    
    public interface RecordListener {
        void onStart();
        void onProgress(int seconds);
        void onComplete(String filePath, long duration);
        void onError(String error);
        void onCancel();
    }
    
    private MediaRecorder mediaRecorder;
    private String outputFilePath;
    private long startTime;
    private boolean isRecording = false;
    private Handler mainHandler;
    private RecordListener listener;
    private Context context;
    
    // 使用独立字段避免匿名类导致的栈溢出问题
    private ProgressUpdater progressUpdater;
    
    // 最小录音时长(秒)
    private static final int MIN_RECORD_DURATION = 1;
    
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
            return;
        }
        
        File voiceDir = new File(Constants.AUDIO_DIR);
        if (!voiceDir.exists()) {
            voiceDir.mkdirs();
        }
        
        outputFilePath = new File(voiceDir, "voice_" + UUID.randomUUID().toString() + ".aac").getAbsolutePath();
        
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(outputFilePath);
            
            mediaRecorder.prepare();
            mediaRecorder.start();
            
            startTime = System.currentTimeMillis();
            isRecording = true;
            
            // 创建并启动进度更新器
            progressUpdater = new ProgressUpdater();
            mainHandler.postDelayed(progressUpdater, 1000);
            
            if (listener != null) {
                listener.onStart();
            }
            
        } catch (Exception e) {
            releaseMediaRecorder();
            if (listener != null) {
                listener.onError("录音启动失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 停止录音
     */
    public void stopRecording() {
        if (!isRecording) {
            return;
        }
        
        isRecording = false;
        mainHandler.removeCallbacks(progressUpdater);
        
        long duration = System.currentTimeMillis() - startTime;
        long durationSeconds = duration / 1000;
        
        if (durationSeconds < MIN_RECORD_DURATION) {
            // 录音时间太短
            releaseMediaRecorder();
            if (outputFilePath != null) {
                new File(outputFilePath).delete();
            }
            if (context != null) {
                mainHandler.post(() -> Toast.makeText(context, "录音时间太短", Toast.LENGTH_SHORT).show());
            }
            if (listener != null) {
                listener.onCancel();
            }
            return;
        }
        
        try {
            mediaRecorder.stop();
        } catch (Exception e) {
            // ignore
        }
        releaseMediaRecorder();
        
        if (listener != null) {
            listener.onComplete(outputFilePath, durationSeconds);
        }
    }
    
    /**
     * 取消录音
     */
    public void cancelRecording() {
        if (!isRecording) {
            return;
        }
        
        isRecording = false;
        mainHandler.removeCallbacks(progressUpdater);
        
        try {
            mediaRecorder.stop();
        } catch (Exception e) {
            // ignore
        }
        releaseMediaRecorder();
        
        if (outputFilePath != null) {
            new File(outputFilePath).delete();
        }
        
        if (listener != null) {
            listener.onCancel();
        }
    }
    
    /**
     * 释放资源
     */
    public void release() {
        cancelRecording();
        if (mainHandler != null && progressUpdater != null) {
            mainHandler.removeCallbacks(progressUpdater);
        }
    }
    
    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception e) {
                // ignore
            }
            mediaRecorder = null;
        }
    }
    
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * 进度更新器 - 独立类避免匿名类递归问题
     */
    private class ProgressUpdater implements Runnable {
        @Override
        public void run() {
            if (isRecording && listener != null) {
                int seconds = (int) ((System.currentTimeMillis() - startTime) / 1000);
                listener.onProgress(seconds);
                mainHandler.postDelayed(this, 1000);
            }
        }
    }
}
