package com.example.lumosonic.ipc;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.bumptech.glide.Glide;
import com.example.lumosonic.R;
import com.example.lumosonic.list.PlaylistBottomSheetFragment;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

import soup.neumorphism.NeumorphImageButton;
import soup.neumorphism.NeumorphTextView;
import soup.neumorphism.ShapeType;

public class MusicActivity extends AppCompatActivity {

    public static final String TAG = "MusicActivity";
    private static final int PERMISSION_REQUEST_CODE = 101; // 权限请求码
    private ShapeableImageView img;
    private NeumorphTextView title;
    private NeumorphTextView artist;
    private NeumorphImageButton btnplay;
    private NeumorphImageButton btnback;
    private NeumorphImageButton btnnext;
    private NeumorphImageButton btnprev;
    private NeumorphImageButton favorite;
    private NeumorphImageButton list;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private SeekBar seekBar;// 进度条
    private ObjectAnimator albumArtAnimator; // 专辑图片动画
    private String currentArtUri = ""; // 当前播放的专辑图片URI
    private IMusicService musicService;
    private boolean isBound = false;
    private final Handler handler = new Handler(Looper.getMainLooper());// 用于更新进度条
    private boolean isUserSeeking = false; // 用户是否正在拖动进度条

    private Song currentSong;



    // 创建回调实例,用于接收服务中的回调,回调的具体实现和UI逻辑
    private final IMusicServiceCallback mCallback = new IMusicServiceCallback.Stub() {
        // 当歌曲切换时
       /* 会触发 onSongChanged 的场景包括：
          1. 首次开始播放：当用户打开应用，点击播放按钮，Activity 调用 musicService.play(0) 时。
          2. 用户点击“下一首”：Activity 调用 musicService.next()，next() 方法内部会调用 play()。
          3. 用户点击“上一首”：Activity 调用 musicService.previous()，previous() 方法内部会调用 play()。
          4. 歌曲播放完成，自动切换：mediaPlayer.setOnCompletionListener 被触发，它会根据播放模式调用 next() 或 play()。
          5. 当前歌曲播放出错，自动切换：onError() 方法被触发，它会调用 next()，next() 再调用 play()。
         简单来说，任何导致当前播放歌曲发生改变的操作，最终都会调用到 play() 方法，从而触发 onSongChanged 回调。
         */
        @Override
        public void onSongChanged(String newTitle, String newArtist, String newAlbumArtUri) {
            // 这是在Binder线程中调用的，需要切回主线程更新UI
            runOnUiThread(() -> {
                title.setText(newTitle);
                artist.setText(newArtist);
                if (newAlbumArtUri != null && !newAlbumArtUri.equals(currentArtUri)) {
                    currentArtUri = newAlbumArtUri;
                    // 使用Glide加载图片
                    Glide.with(MusicActivity.this)
                            .load(newAlbumArtUri)
                            .placeholder(R.drawable.ic_album_placeholder)
                            .error(R.drawable.tou_1)
                            .into(img);
                } else if (newAlbumArtUri == null) {
                    img.setImageResource(R.drawable.tou_1);
                }
            });
        }

        // 当播放状态改变时
        /* 会触发 onPlaybackStateChanged 的场景包括：
           1. 开始播放一首歌时：任何导致 play() 方法被执行的场景（见上文），最终都会触发 onPlaybackStateChanged(true)。
           2. 用户点击播放/暂停按钮（当前是播放状态）：Activity 调用 musicService.pause()，触发 onPlaybackStateChanged(false)。
           3. 用户点击播放/暂停按钮（当前是暂停状态）：Activity 调用 musicService.resume()，触发 onPlaybackStateChanged(true)。
           */
        @Override
        public void onPlaybackStateChanged(boolean isPlaying) {
            runOnUiThread(() -> {
                updatePlayPauseButton(isPlaying); // 把更新按钮的逻辑抽成一个方法
                updateAlbumArtAnimation(isPlaying); // 把更新专辑图片动画的逻辑抽成一个方法
            });
        }
    };

    // 服务连接和回调
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = IMusicService.Stub.asInterface(service);
            isBound = true;
            try {
                // 注册回调,该Activity作为回调的接收端加入mCallback列表
                musicService.registerCallback(mCallback);
                // 同步一次UI状态,防止服务已在后台播放但UI未更新
                syncUIState();
                boolean isFav = musicService.isFavorite(currentSong.getId());
                if (isFav) {
                    // 数据状态为 true (已收藏)，所以显示“已收藏”的图标
                    favorite.setImageResource(R.drawable.ic_favorite); // 假设 ic_favorite 是实心（已收藏）图标
                } else {
                    // 数据状态为 false (未收藏)，所以显示“未收藏”的图标
                    favorite.setImageResource(R.drawable.ic_notfavorite); // 假设 ic_notfavorite 是空心（未收藏）图标
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            // 你仍然需要Handler来更新进度条,启动专门用于更新进度条的循环
            handler.post(updateProgressRunnable);
        }

        // 服务断开连接时,注销回调
        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
            isBound = false;
            // 服务断开连接时,注销回调
            try {
                if (musicService != null) {
                    musicService.unregisterCallback(mCallback);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music);
        // 沉浸式状态栏
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        initViews();
        handleIntentExtras(); // 处理intent中的数据
        if (checkPermission()) {
            loadMusicAndBindService(); // 检查权限并加载音乐
        } else {
            requestPermission(); // 请求权限
        }
    }

    private void handleIntentExtras() {
        Intent intent = getIntent();
        // 检查 Intent 是否存在，并且是否包含我们约定的 "SELECTED_SONG" 钥匙
        if (intent != null && intent.hasExtra("SELECTED_SONG")) {
            Song song;
            // 从 Android 13 (TIRAMISU) 开始，获取 Parcelable 对象需要新的方法
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                song = intent.getParcelableExtra("SELECTED_SONG", Song.class);
            } else {
                // 对旧版本的兼容写法
                song = intent.getParcelableExtra("SELECTED_SONG");
            }

            // 如果成功取出了 Song 对象
            if (song != null) {
                // 更新UI，不等回调！
                title.setText(song.getTitle());
                artist.setText(song.getArtist());

                // 更新当前封面URI的记录，防止后续回调重复加载
                currentArtUri = song.getAlbumArtUri();

                // 使用 Glide 加载封面图片
                Glide.with(MusicActivity.this)
                        .load(currentArtUri)
                        .placeholder(R.drawable.ic_album_placeholder)
                        .error(R.drawable.tou_1)
                        .into(img);
            }
        }
    }

    private void initViews() {
        img = findViewById(R.id.image_album_art);
        title = findViewById(R.id.text_song_title);
        artist = findViewById(R.id.text_artist);
        btnplay = findViewById(R.id.button_play_pause);
        btnback = findViewById(R.id.button_back);
        btnnext = findViewById(R.id.button_next);
        btnprev = findViewById(R.id.button_previous);
        favorite = findViewById(R.id.button_favorite);
        list = findViewById(R.id.button_playlist);
        NeumorphImageButton shuffle = findViewById(R.id.button_shuffle);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        seekBar = findViewById(R.id.seekbar_song_progress);
        NeumorphImageButton download = findViewById(R.id.button_download);

        btnback.setOnClickListener(v -> finish());

        // 播放按钮点击事件
        btnplay.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                try {
                    if (musicService.isPlaying()) {
                        musicService.pause();
                    } else {
                        if (musicService.getCurrentPosition() > 0) {
                            musicService.resume();
                        } else {
                            musicService.play(0);
                        }
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        btnnext.setOnClickListener(v -> {
            if (isBound) {
                try {
                    musicService.next();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        btnprev.setOnClickListener(v -> {
            if (isBound) {
                try {
                    musicService.previous();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        // 播放模式切换
        shuffle.setOnClickListener(v -> {
            if (isBound) {
                try {
                    switch (musicService.getPlayMode()) {
                        case MusicService.PLAY_MODE_LIST:
                            musicService.setPlayMode(MusicService.PLAY_MODE_SHUFFLE);
                            Toast.makeText(this, "随机播放", Toast.LENGTH_SHORT).show();
                            break;
                        case MusicService.PLAY_MODE_SHUFFLE:
                            musicService.setPlayMode(MusicService.PLAY_MODE_REPEAT);
                            Toast.makeText(this, "单曲循环", Toast.LENGTH_SHORT).show();
                            break;
                        case MusicService.PLAY_MODE_REPEAT:
                            musicService.setPlayMode(MusicService.PLAY_MODE_LIST);
                            Toast.makeText(this, "顺序播放", Toast.LENGTH_SHORT).show();
                            break;
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        list.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                PlaylistBottomSheetFragment bottomSheet = PlaylistBottomSheetFragment.newInstance();
                // 将 musicService 实例传递给 Fragment
                bottomSheet.setMusicService(musicService);
                // 显示 BottomSheet
                bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
            } else {
                Toast.makeText(this, "服务未连接", Toast.LENGTH_SHORT).show();
            }
        });

        // 下载按钮点击事件
        download.setOnClickListener(v -> {
            if (isBound) {
                try {
                    musicService.downloadCurrentSong();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        // 收藏按钮点击事件
        favorite.setOnClickListener(v -> {
            // 确保服务已绑定且有正在播放的歌曲
            if (isBound && musicService != null && currentSong != null) {
                try {

                    // 关键逻辑：
                    // 1. 首先，我们获取按钮【点击之前】的收藏状态
                    boolean isFavoriteBeforeClick = musicService.isFavorite(currentSong.getId());

                    // 2. 然后，我们命令Service在后台正常切换收藏状态
                    //    (例如，如果之前是false，现在数据已经变成true了)
                    musicService.toggleFavorite(currentSong);

                    // 3. 最后，我们用【点击之前】的状态来更新UI
                    //    这样就实现了您要的“点击后保持不变”的延迟效果
                    updateFavoriteButton(isFavoriteBeforeClick);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });



        // SeekBar拖动事件监听,更新时间文本
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 当用户拖动时,更新当前时间文本
                if (fromUser) {
                    tvCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 用户开始拖动,设置标记位
                isUserSeeking = true;
                handler.removeCallbacks(updateProgressRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 用户停止拖动,发送seekTo命令
                if (isBound && musicService != null) {
                    try {
                        musicService.seekTo(seekBar.getProgress());
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                // 取消标记位
                isUserSeeking = false;
                // 立即启动Handler,让UI尽快同步
                handler.postDelayed(updateProgressRunnable, 100); // 延迟100毫秒再启动，给seekTo一点缓冲时间
            }
        });
        // 设置专辑封面的动画
        setupAlbumArtAnimator();
    }


    // 加载音乐并绑定服务
    private void loadMusicAndBindService() {
        // 在App和Activity两处都调用 startService(),是一种“双保险”策略,
        // 它可以让你的App在一种特殊但重要的场景下正常工作：App进程被系统回收后恢复.
        Intent intent = new Intent(this, MusicService.class);
        // 如果服务还未运行,startService会调用onCreate()加载音乐
        startService(intent);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    // 检查权限
    private boolean checkPermission() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_AUDIO;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    // 请求权限
    private void requestPermission() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_AUDIO;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQUEST_CODE);
    }

    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult( requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMusicAndBindService(); // 绑定服务
            } else {
                Toast.makeText(this, "Permission Denied to read audio files", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 它只负责更新进度条和时间相关的UI
    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isBound && musicService != null && !isUserSeeking) {
                try {
                    int totalDuration = musicService.getDuration();
                    int currentPosition = musicService.getCurrentPosition();
                    seekBar.setMax(totalDuration);
                    seekBar.setProgress(currentPosition);
                    tvTotalTime.setText(formatTime(totalDuration));
                    tvCurrentTime.setText(formatTime(currentPosition));

                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            handler.postDelayed(this, 500);
        }
    };


    // 连接服务后,立即同步一次UI,避免打开界面时状态显示不正确
    private void syncUIState() throws RemoteException {
        if (!isBound || musicService != null) {
            int currentIndex = musicService.getCurrentIndex();
            // 从 Service 获取真实的播放列表
            List<Song> playlist = musicService.getPlaylist();
            String initialTitle = musicService.getSongTitle();
            String initialArtist = musicService.getSongArtist();
            String initialArtUri = musicService.getCurrentAlbumArtUri();
            // 检查当前是否有歌曲在播放
            if (currentIndex >= 0 && playlist != null && !playlist.isEmpty() && currentIndex < playlist.size()) {
                currentSong = playlist.get(currentIndex);
            }else{
                currentSong = new Song(0, initialTitle, initialArtist, "", 0, initialArtUri);
                updateFavoriteButton(false);
            }
            mCallback.onSongChanged(initialTitle, initialArtist, initialArtUri);
            // 更新播放/暂停状态
            boolean isPlaying = musicService.isPlaying();
            mCallback.onPlaybackStateChanged(isPlaying);
        }
    }


    // 正确的、数据驱动的UI更新方法
    private void updateFavoriteButton(boolean isFavorite) {
        if (isFavorite) {
            favorite.setImageResource(R.drawable.ic_notfavorite); // 假设 ic_favorite 是实心（已收藏）图标
        } else {
            favorite.setImageResource(R.drawable.ic_favorite); // 假设 ic_notfavorite 是空心（未收藏）图标
        }
    }
    // 专门用于更新播放或暂停按钮的UI,由回调调用
    private void updatePlayPauseButton(boolean isPlaying) {
        if (isPlaying && btnplay != null) {
            btnplay.setImageResource(R.drawable.ic_pause);
            btnplay.setShapeType(ShapeType.PRESSED);
        } else {
            btnplay.setImageResource(R.drawable.ic_play);
            btnplay.setShapeType(ShapeType.FLAT);
        }
    }

    // 专门用于控制封面旋转动画,由回调调用
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

    // 格式化时间
    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // 设置专辑图片的动画
    private void setupAlbumArtAnimator() {
        // 创建ObjectAnimator的实例
        // img: 我们要执行动画的目标视图
        // "rotation": 我们要改变的属性是“旋转角度”
        // 0f, 360f: 动画从 0 度旋转到 360 度
        albumArtAnimator = ObjectAnimator.ofFloat(img, "rotation", 0f, 360f);

        albumArtAnimator.setDuration(20000); // 旋转一周的时长
        albumArtAnimator.setRepeatCount(ObjectAnimator.INFINITE); // 无限循环
        albumArtAnimator.setRepeatMode(ObjectAnimator.RESTART); // 每次都从0度重新开始

        // 设置插值器为线性,确保匀速旋转
        albumArtAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 在Activity销毁时,必须反注册回调并解绑服务,防止内存泄漏
        if (isBound && musicService != null) {
            try {
                musicService.unregisterCallback(mCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            unbindService(serviceConnection);
            isBound = false;
        }
        // 移除所有待处理的回调,防止内存泄漏
        handler.removeCallbacks(updateProgressRunnable);
        // 停止专辑图片的动画
        if (albumArtAnimator != null) {
            albumArtAnimator.cancel();
        }
    }
}