package io.openim.android.ouiconversation.ui.fragment;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;

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
import io.openim.android.ouiconversation.vm.ChatVM;
import io.openim.android.ouiconversation.widget.VoiceRecorder;
import io.openim.android.ouicore.adapter.RecyclerViewAdapter;
import io.openim.android.ouicore.base.BaseApp;
import io.openim.android.ouicore.base.BaseFragment;
import io.openim.android.ouicore.services.CallingService;
import io.openim.android.ouicore.utils.Constants;
import io.openim.android.ouicore.utils.GetFilePathFromUri;
import io.openim.android.ouicore.utils.GlideEngine;
import io.openim.android.ouicore.utils.HasPermissions;
import io.openim.android.ouicore.utils.L;
import io.openim.android.ouicore.utils.MThreadTool;
import io.openim.android.ouicore.utils.MediaFileUtil;
import io.openim.android.ouicore.utils.Routes;
import io.openim.android.sdk.OpenIMClient;
import io.openim.android.sdk.models.Message;

/**
 * 输入框扩展菜单Fragment (+号菜单)
 * 包含：相册、文件、视频通话
 */
public class InputExpandFragment extends BaseFragment<ChatVM> {
    
    // 菜单图标列表
    public static List<Integer> menuIcons =
        Arrays.asList(
            R.mipmap.ic_c_photo,           // 0 - 相册
            R.mipmap.ic_c_file,            // 1 - 文件
            R.mipmap.ic_c_call             // 2 - 视频通话
        );
    
    // 菜单标题列表
    public static List<String> menuTitles =
        Arrays.asList(
            BaseApp.inst().getString(io.openim.android.ouicore.R.string.album),
            BaseApp.inst().getString(io.openim.android.ouicore.R.string.file),
            BaseApp.inst().getString(io.openim.android.ouicore.R.string.video_calls)
        );

    FragmentInputExpandBinding v;
    private HasPermissions hasStorage;
    
    // 文件选择器启动器
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MThreadTool.executorService.execute(() -> {
            hasStorage = new HasPermissions(getActivity(), Permission.MANAGE_EXTERNAL_STORAGE);
        });
        
        // 初始化文件选择器
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        sendFileMessage(uri);
                    }
                }
            }
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
                                // 相册
                                showMediaPicker();
                                break;
                            case 1:
                                // 文件
                                showFilePicker();
                                break;
                            case 2:
                                // 视频通话
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
     * 显示文件选择器
     */
    private void showFilePicker() {
        if (hasStorage == null) {
            Toast.makeText(getContext(), "权限初始化中，请重试", Toast.LENGTH_SHORT).show();
            return;
        }
        
        hasStorage.safeGo(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            // 允许选择所有类型的文件
            String[] mimeTypes = {
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/plain",
                "application/zip",
                "application/x-rar-compressed",
                "image/*",
                "video/*",
                "audio/*"
            };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            filePickerLauncher.launch(intent);
        });
    }
    
    /**
     * 发送文件消息
     */
    private void sendFileMessage(Uri uri) {
        if (uri == null || getContext() == null) return;
        
        try {
            // 获取文件路径
            String filePath = GetFilePathFromUri.getFileAbsolutePath(getContext(), uri);
            if (filePath == null || filePath.isEmpty()) {
                Toast.makeText(getContext(), "无法获取文件路径", Toast.LENGTH_SHORT).show();
                return;
            }
            
            File file = new File(filePath);
            if (!file.exists()) {
                Toast.makeText(getContext(), "文件不存在", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 获取文件名
            String fileName = file.getName();
            
            // 创建文件消息 - 使用 OpenIM SDK
            Message fileMsg = OpenIMClient.getInstance().messageManager
                .createFileMessageFromFullPath(filePath, fileName);
            
            if (fileMsg != null && vm != null) {
                vm.sendMsg(fileMsg);
                Toast.makeText(getContext(), "文件发送成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "文件发送失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            L.e("Send file error: " + e.getMessage());
            Toast.makeText(getContext(), "文件发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
