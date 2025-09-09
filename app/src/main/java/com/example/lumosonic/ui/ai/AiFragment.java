package com.example.lumosonic.ui.ai;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.acrcloud.rec.ACRCloudClient;
import com.acrcloud.rec.ACRCloudConfig;
import com.acrcloud.rec.ACRCloudResult;
import com.airbnb.lottie.LottieAnimationView;
import com.example.lumosonic.databinding.FragmentAiBinding;
import com.example.lumosonic.intent.SearchActivity;
import com.acrcloud.rec.IACRCloudListener;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// 实现 ACRCloud 的监听器接口
public class AiFragment extends Fragment implements IACRCloudListener {

    private FragmentAiBinding binding;
    private static final String TAG = "AiFragment";
    private ACRCloudClient mClient; // 用于识别音乐
    private ACRCloudConfig mConfig; // 用于配置识别参数
    private boolean mProcessing = false; // 创建一个变量来跟踪是否正在处理识别结果
    private boolean initState = false; // 创建一个变量来跟踪初始化状态
    private ACRCloudResult results; // 创建一个变量来保存识别结果

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAiBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initAcrCloudRecognizer();// 初始化 ACRCloud SDK

        // 获取Lottie动画
        final LottieAnimationView lottieAnimation = binding.lottieSearch;

        // 修改触摸事件以调用ACRCloud SDK
        lottieAnimation.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lottieAnimation.playAnimation(); // 播放动画
                        startRecognize(); // 开始识别
                        return true;

                    case MotionEvent.ACTION_UP:
                        lottieAnimation.pauseAnimation(); // 暂停动画
                        lottieAnimation.setProgress(0f); // 重置动画进度
                        cancelRecognize(); // 取消识别
                        return true;
                }
                return false;
            }
        });
    }

    private void initAcrCloudRecognizer() {


        this.mConfig = new ACRCloudConfig();
        this.mConfig.acrcloudListener = this;
        this.mConfig.context = requireActivity();

        this.mConfig.host = "identify-cn-north-1.acrcloud.cn";
        this.mConfig.accessKey = "031b4afb4768f59f5b63cfe07f20d72b";
        this.mConfig.accessSecret = "HiV1GDffodSPM8YUp6RBNqWdxuUensaTCziEuPI0";



        // 如果不需要音量回调,可以设置为 false
        this.mConfig.recorderConfig.isVolumeCallback = true;

         // 创建 ACRCloudClient 实例
        this.mClient = new ACRCloudClient();
        this.initState = this.mClient.initWithConfig(this.mConfig);

        if (!this.initState) {
            showToast("识别服务初始化失败");
        }
    }

    public void startRecognize() {
        if (!this.initState) {
            showToast("初始化错误");
            return;
        }

        if (!mProcessing) {
            mProcessing = true;
            showToast("正在识别...");
            if (this.mClient == null || !this.mClient.startRecognize()) {
                mProcessing = false;
                showToast("启动识别失败");
            }
        }
    }

    // 停止识别
    public void cancelRecognize() {
        if (mProcessing && this.mClient != null) {
            this.mClient.cancel();
        }
        mProcessing = false;
    }

    // 实现ACRCloud的回调方法
    @Override
    public void onResult(ACRCloudResult results) {
        this.results = results;
        // 识别结束后.重置状态
        mProcessing = false;

        // 确保动画停止
        if (binding != null) {
            binding.lottieSearch.pauseAnimation();
            binding.lottieSearch.setProgress(0f);
        }

        String result = results.getResult();
        Log.d(TAG, "Result: " + result);

        try {
            JSONObject json = new JSONObject(result);
            JSONObject status = json.getJSONObject("status");
            int code = status.getInt("code");

            if (code == 0) { // 识别成功
                JSONObject metadata = json.getJSONObject("metadata");
                if (metadata.has("music")) {
                    JSONArray musics = metadata.getJSONArray("music");
                    if (musics.length() > 0) {
                        JSONObject firstMatch = musics.getJSONObject(0);
                        String title = firstMatch.getString("title");
                        String artist = firstMatch.getJSONArray("artists").getJSONObject(0).getString("name");

                        String songTitle = title + " " + artist;
                        showToast("识别成功: " + songTitle);

                        // 跳转到搜索页面
                        Intent intent = new Intent(getActivity(), SearchActivity.class);
                        intent.putExtra("SEARCH_KEYWORD", songTitle);
                        startActivity(intent);
                        return; // 成功后直接返回
                    }
                }
                // 有元数据但没有音乐信息
                showToast("未能识别到歌曲");

            } else {
                String msg = status.getString("msg");
                showToast("识别失败: " + msg);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            showToast("未能识别到歌曲 (数据解析错误)");
        }
    }

    @Override
    public void onVolumeChanged(double volume) {
        // 这个回调会频繁触发,可以用来更新UI上的音量动画等

    }

    private void showToast(String message) {
        if (getActivity() != null) {
            // 确保在主线程更新 UI
            getActivity().runOnUiThread(() -> Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 在Fragment销毁时释放资源
        if (this.mClient != null) {
            this.mClient.release();
            this.initState = false;
            this.mClient = null;
        }
        binding = null;
    }
}