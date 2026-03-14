package io.openim.android.ouiconversation.ui.fragment;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.alibaba.android.arouter.launcher.ARouter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hjq.permissions.Permission;
import com.luck.picture.lib.basic.PictureSelector;
import com.luck.picture.lib.config.SelectMimeType;
import com.luck.picture.lib.entity.LocalMedia;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.openim.android.ouiconversation.R;
import io.openim.android.ouiconversation.databinding.FragmentInputExpandBinding;
import io.openim.android.ouiconversation.databinding.ItemExpandMenuBinding;
import io.openim.android.ouiconversation.ui.ChatActivity;
import io.openim.android.ouiconversation.ui.ShootActivity;
import io.openim.android.ouiconversation.vm.ChatVM;
import io.openim.android.ouiconversation.widget.VoiceRecorder;
import io.openim.android.ouicore.adapter.RecyclerViewAdapter;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.base.BaseFragment;
import io.openim.android.ouicore.base.vm.injection.Easy;
import io.openim.android.ouicore.databinding.LayoutCommonDialogBinding;
import io.openim.android.ouicore.ex.MultipleChoice;
import io.openim.android.ouicore.services.CallingService;
import io.openim.android.ouicore.utils.ActivityManager;
import io.openim.android.ouicore.utils.Common;
import io.openim.android.ouicore.utils.Constants;
import io.openim.android.ouicore.utils.GetFilePathFromUri;
import io.openim.android.ouicore.utils.GlideEngine;
import io.openim.android.ouicore.utils.HasPermissions;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.MThreadTool;
import io.openim.android.ouicore.utils.MediaFileUtil;
import io.openim.android.ouicore.utils.Routes;
import io.openim.android.ouicore.vm.SelectTargetVM;
import io.openim.android.ouicore.widget.CommonDialog;
import io.openim.android.ouicore.widget.WebViewActivity;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.models.CardElem;
import io.openim.android.sdk.models.Message;

public class InputExpandFragment extends BaseFragment<ChatVM> {
    public static List<Integer> menuIcons =
        Arrays.asList(
            io.openim.android.ouicore.R.mipmap.ic_chat_photo, 
            io.openim.android.ouicore.R.mipmap.ic_voice_s1,
            io.openim.android.ouicore.R.mipmap.ic_tools_video_call);
    public static List<String> menuTitles =
        Arrays.asList(
            BaseApp.inst().getString(io.openim.android.ouicore.R.string.album),
            BaseApp.inst().getString(io.openim.android.ouicore.R.string.voice),
            BaseApp.inst().getString(io.openim.android.ouicore.R.string.video_calls));

    FragmentInputExpandBinding v;
    // permissions
    private HasPermissions hasStorage;
    private HasPermissions hasRecordAudio;
    
    // Voice recording
    private VoiceRecorder voiceRecorder;
    private Dialog voiceRecordDialog;
    private TextView recordDurationText;
    private Button btnRecord;
    private boolean isRecording = false;
    private String recordedFilePath;
    private int recordedDuration;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MThreadTool.executorService.execute(() -> {
            hasStorage = new HasPermissions(getActivity(), Permission.MANAGE_EXTERNAL_STORAGE);
            hasRecordAudio = new HasPermissions(getActivity(), Permission.RECORD_AUDIO);
        });
        voiceRecorder = new VoiceRecorder(getContext());
        voiceRecorder.setRecordListener(new VoiceRecorder.RecordListener() {
            @Override
            public void onStart() {
                isRecording = true;
                updateRecordUI();
            }

            @Override
            public void onProgress(int seconds) {
                if (recordDurationText != null) {
                    recordDurationText.setText(seconds + "\"");
                }
            }

            @Override
            public void onComplete(String filePath, int duration) {
                isRecording = false;
                recordedFilePath = filePath;
                recordedDuration = duration;
                updateRecordUI();
                // 自动发送语音
                sendVoiceMessage();
                dismissVoiceDialog();
            }

            @Override
            public void onError(String error) {
                isRecording = false;
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                dismissVoiceDialog();
            }

            @Override
            public void onCancel() {
                isRecording = false;
                dismissVoiceDialog();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (voiceRecorder != null) {
            voiceRecorder.release();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        v = FragmentInputExpandBinding.inflate(inflater);
        init();
        return v.getRoot();
    }

    private void init() {
        v.getRoot().setLayoutManager(new GridLayoutManager(getContext(), 4));
        RecyclerViewAdapter adapter =
            new RecyclerViewAdapter<Object, ExpandHolder>(ExpandHolder.class) {

                @Override
                public void onBindView(@NonNull ExpandHolder holder, Object data, int position) {
                    holder.v.menu.setCompoundDrawablesRelativeWithIntrinsicBounds(null,
                        getContext().getDrawable(menuIcons.get(position)), null, null);
                    holder.v.menu.setText(menuTitles.get(position));
                    holder.v.menu.setOnClickListener(v -> {
                        switch (position) {
                            case 0:
                                showMediaPicker();
                                break;
                            case 1:
                                startVoiceRecording();
                                break;
                            case 2:
                                goToCall();
                                break;
                        }
                    });
                }
            };
        v.getRoot().setAdapter(adapter);
        adapter.setItems(menuIcons);
    }

    /**
     * 显示语音录制对话框
     */
    private void startVoiceRecording() {
        hasRecordAudio.safeGo(() -> {
            showVoiceRecordDialog();
        });
    }
    
    /**
     * 显示录音对话框
     */
    private void showVoiceRecordDialog() {
        if (getContext() == null) return;
        
        voiceRecordDialog = new Dialog(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_voice_record, null);
        voiceRecordDialog.setContentView(dialogView);
        voiceRecordDialog.setCancelable(false);
        
        ImageView voiceIcon = dialogView.findViewById(R.id.voiceIcon);
        recordDurationText = dialogView.findViewById(R.id.recordDuration);
        TextView recordHint = dialogView.findViewById(R.id.recordHint);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        btnRecord = dialogView.findViewById(R.id.btnRecord);
        Button btnSend = dialogView.findViewById(R.id.btnSend);
        
        // 取消按钮
        btnCancel.setOnClickListener(v -> {
            if (isRecording) {
                voiceRecorder.cancelRecording();
            }
            dismissVoiceDialog();
        });
        
        // 录音按钮 - 按住录音，松开停止
        btnRecord.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 开始录音
                    voiceRecorder.startRecording();
                    btnRecord.setBackgroundResource(R.drawable.bg_recording_button);
                    btnRecord.setText("松开停止");
                    return true;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 停止录音
                    if (isRecording) {
                        voiceRecorder.stopRecording();
                    }
                    btnRecord.setBackgroundResource(R.drawable.bg_confirm_button);
                    btnRecord.setText("按住录音");
                    return true;
            }
            return false;
        });
        
        // 发送按钮（用于手动发送，这里自动发送所以隐藏）
        btnSend.setOnClickListener(v -> {
            sendVoiceMessage();
            dismissVoiceDialog();
        });
        
        voiceRecordDialog.show();
    }
    
    /**
     * 更新录音 UI
     */
    private void updateRecordUI() {
        if (voiceRecordDialog == null || !voiceRecordDialog.isShowing()) return;
        
        View dialogView = voiceRecordDialog.findViewById(android.R.id.content);
        if (dialogView == null) return;
        
        TextView recordHint = dialogView.findViewById(R.id.recordHint);
        Button btnSend = dialogView.findViewById(R.id.btnSend);
        
        if (isRecording) {
            recordDurationText.setVisibility(View.VISIBLE);
            recordHint.setText("正在录音...");
            btnSend.setVisibility(View.GONE);
        } else if (recordedFilePath != null) {
            recordHint.setText("录音完成，时长: " + recordedDuration + "秒");
            btnSend.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 关闭录音对话框
     */
    private void dismissVoiceDialog() {
        if (voiceRecordDialog != null && voiceRecordDialog.isShowing()) {
            voiceRecordDialog.dismiss();
        }
        voiceRecordDialog = null;
        recordedFilePath = null;
        recordedDuration = 0;
    }
    
    /**
     * 发送语音消息
     */
    private void sendVoiceMessage() {
        if (recordedFilePath == null || recordedFilePath.isEmpty()) {
            return;
        }
        
        File voiceFile = new File(recordedFilePath);
        if (!voiceFile.exists()) {
            Toast.makeText(getContext(), "录音文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 使用 OpenIM SDK 创建语音消息
        Message voiceMsg = OpenIMClient.getInstance().messageManager
            .createVoiceMessageFromFullPath(recordedFilePath, recordedDuration);
        
        if (voiceMsg != null && vm != null) {
            vm.sendMsg(voiceMsg);
            Toast.makeText(getContext(), "语音发送成功", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "语音消息创建失败", Toast.LENGTH_SHORT).show();
        }
    }

    private final ActivityResultLauncher<Intent> captureLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            try {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    ArrayList<LocalMedia> files = PictureSelector.obtainSelectorList(data);

                    for (LocalMedia file : files) {
                        String path = GetFilePathFromUri.getFileAbsolutePath(InputExpandFragment.this.getActivity() ,Uri.parse(file.getAvailablePath()));
                        Message msg = null;
                        if (MediaFileUtil.isImageType(path)) {
                            msg =
                                OpenIMClient.getInstance().messageManager.createImageMessageFromFullPath(path);
                        }
                        if (MediaFileUtil.isVideoType(path)) {
                            Glide.with(this).asBitmap().load(path).into(new SimpleTarget<Bitmap>() {
                                @Override
                                public void onResourceReady(@NonNull Bitmap resource,
                                                            @Nullable Transition<? super Bitmap> transition) {
                                    String firstFame = MediaFileUtil.saveBitmap(resource,
                                        Constants.PICTURE_DIR, false);
                                    long duration = MediaFileUtil.getDuration(path) / 1000;
                                    Message msg =
                                        OpenIMClient.getInstance().messageManager.createVideoMessageFromFullPath(path, MediaFileUtil.getFileType(path).mimeType, duration, firstFame);
                                    vm.sendMsg(msg);
                                }
                            });
                            continue;
                        }
                        if (null == msg)
                            msg =
                                OpenIMClient.getInstance().messageManager.createTextMessage("[" + getString(io.openim.android.ouicore.R.string.unsupported_type) + "]");
                        vm.sendMsg(msg);
                    }
                }
            } catch (Exception e) {
                L.e(e.getMessage());
            }
        });

    @SuppressLint("unchecked")
    private void showMediaPicker() {
        hasStorage.safeGo(() -> {
            try {
                PictureSelector.create(this)
                    .openGallery(SelectMimeType.ofAll())
                    .setImageEngine(GlideEngine.createGlideEngine())
                    .setMaxVideoSelectNum(9)
                    .setMaxSelectNum(9)
                    .forResult(captureLauncher);
            } catch (Exception e) {
                L.e(e.getMessage());
            }
        });
    }

    private void goToCall() {
        CallingService callingService =
            (CallingService) ARouter.getInstance().build(Routes.Service.CALLING).navigation();
        if (null == callingService) return;
        ChatActivity activity = (ChatActivity) getActivity();
        if (null != activity) {
            activity.goToCall();
        }
    }

    public void setChatVM(ChatVM vm) {
        this.vm = vm;
    }

    public static class ExpandHolder extends RecyclerView.ViewHolder {
        public ItemExpandMenuBinding v;

        public ExpandHolder(@NonNull View itemView) {
            super(ItemExpandMenuBinding.inflate(LayoutInflater.from(itemView.getContext())).getRoot());
            v = ItemExpandMenuBinding.bind(this.itemView);
        }
    }
}
