package com.example.lumosonic.ui.home;

import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.lumosonic.ipc.MusicActivity;
import com.example.lumosonic.R;
import com.example.lumosonic.intent.SearchActivity;
import com.example.lumosonic.databinding.FragmentHomeBinding;
import com.example.lumosonic.ipc.IMusicService;
import com.example.lumosonic.ipc.IMusicServiceCallback;
import com.example.lumosonic.ipc.MusicService;
import com.example.lumosonic.list.PlaylistBottomSheetFragment;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel; // 将ViewModel提升为成员变量
    private IMusicService musicService;
    private boolean isBound = false;
    private ObjectAnimator albumArtAnimator; // 封面旋转动画

    // 连接服务
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = IMusicService.Stub.asInterface(service);
            isBound = true;
            try {
                // 连接服务成功,注册回调
                musicService.registerCallback(mCallback);
                // 立即同步UI
                syncUIState();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 断开服务
        @Override
        public void onServiceDisconnected(ComponentName name) {
            try {
                if (musicService != null && musicService.asBinder().isBinderAlive()) {
                    musicService.unregisterCallback(mCallback);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            musicService = null;
            isBound = false;
        }
    };

    // 实现Service回调
    private final IMusicServiceCallback mCallback = new IMusicServiceCallback.Stub() {

        @Override
        public void onSongChanged(String title, String artist, String albumArtUri) throws RemoteException {
            // 通知更新专辑封面URI
            homeViewModel.onSongChanged(albumArtUri);
        }

        @Override
        public void onPlaybackStateChanged(boolean playing) throws RemoteException {
            // 通知更新播放状态
            homeViewModel.onPlaybackStateChanged(playing);
        }
    };



    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
       /* homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);*/
        // HomeFragment.java -> onCreateView 方法内
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViewListeners();
        // 设置动画并观察ViewModel
        setupAlbumArtAnimator();
        observeViewModel();
    }

    @Override
    public void onStart() {
        super.onStart();
        // 绑定服务
        Intent intent = new Intent(getActivity(), MusicService.class);
        // BIND_AUTO_CREATE表示如果服务还没创建,就先创建它
        getActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        // 解绑服务
        if (isBound) {
            getActivity().unbindService(serviceConnection);
            isBound = false;
        }
    }

    // 观察ViewModel的LiveData来更新UI
    private void observeViewModel() {
        homeViewModel.getAlbumArtUri().observe(getViewLifecycleOwner(), uri -> {
            if (uri != null) {
                Glide.with(this)
                        .load(uri)
                        .placeholder(R.drawable.ic_album_placeholder)
                        .error(R.drawable.tou_1)
                        .into(binding.imageAlbum);
            } else {
                // 显示默认专辑封面
                binding.imageAlbum.setImageResource(R.drawable.tou_1);
            }
        });


        // 播放状态
        homeViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            // 用于控制封面旋转
            updateAlbumArtAnimation(playing);

            // 用于控制音符线动画
            if (playing) {
                // 显示并播放动画
                binding.leftWave.setVisibility(View.VISIBLE);
                binding.rightWave.setVisibility(View.VISIBLE);
                binding.leftWave.playAnimation();
                binding.rightWave.playAnimation();
            } else {
                // 暂停并隐藏动画
                binding.leftWave.pauseAnimation();
                binding.rightWave.pauseAnimation();
                binding.leftWave.setVisibility(View.GONE);
                binding.rightWave.setVisibility(View.GONE);
            }
        });
    }

    // 初始化界面
    // 初始化点击事件监听器
    private void initViewListeners() {
        binding.imageAlbum.setOnClickListener(v -> startActivity(new Intent(getActivity(), MusicActivity.class)));
        binding.btn1.setOnClickListener(v -> startActivity(new Intent(getActivity(), MusicActivity.class)));
        binding.btn2.setOnClickListener(v -> {
            PlaylistBottomSheetFragment bottomSheet = PlaylistBottomSheetFragment.newInstance();
            // 将 musicService 实例传递给 Fragment
            bottomSheet.setMusicService(musicService);
            // 显示 BottomSheet
            bottomSheet.show(getParentFragmentManager(), bottomSheet.getTag());
        });
        binding.btn3.setOnClickListener(v -> startActivity(new Intent(getActivity(), RecentMusicActivity.class)));
        binding.searchButton.setOnClickListener(v -> {
            // 获取EditText中的文本
            String keyword = binding.searchEditText.getText().toString().trim();
            // 检查文本是否为空
            if (keyword.isEmpty()) {
                Toast.makeText(getActivity(), "请输入搜索内容", Toast.LENGTH_SHORT).show();
                return;
            }

            // 创建Intent并把关键字放进去
            Intent intent = new Intent(getActivity(), SearchActivity.class);
            intent.putExtra("SEARCH_KEYWORD", keyword); // 使用一个常量作为Key

            // 启动Activity
            startActivity(intent);
        });
    }

    // 设置专辑图片的动画
    private void setupAlbumArtAnimator() {
        albumArtAnimator = ObjectAnimator.ofFloat(binding.imageAlbum, "rotation", 0f, 360f);
        albumArtAnimator.setDuration(20000);
        albumArtAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        albumArtAnimator.setRepeatMode(ObjectAnimator.RESTART);
        albumArtAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
    }

    // 专门用于控制封面旋转动画
    private void updateAlbumArtAnimation(boolean isPlaying) {
        if (isPlaying) {
            if (!albumArtAnimator.isStarted()) {
                albumArtAnimator.start();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && albumArtAnimator.isPaused()) {
                albumArtAnimator.resume();
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && albumArtAnimator.isRunning()) {
                albumArtAnimator.pause();
            }
        }
    }

    // 连接服务后,立即同步一次UI
    private void syncUIState() throws RemoteException {
        if (isBound && musicService != null) {
            // 同步歌曲信息和播放状态,通知ViewModel
            homeViewModel.onSongChanged(musicService.getCurrentAlbumArtUri());
            homeViewModel.onPlaybackStateChanged(musicService.isPlaying());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (albumArtAnimator != null) {
            albumArtAnimator.cancel();
        }
        binding = null;
    }
}