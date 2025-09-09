package com.example.lumosonic.ipc;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;

import com.example.lumosonic.R;
import com.example.lumosonic.room.AppDatabase;
import com.example.lumosonic.room.favorite.FavoriteSongDao;
import com.example.lumosonic.room.favorite.FavoriteSongEntity;
import com.example.lumosonic.room.playlist.PlaylistSongDao;
import com.example.lumosonic.room.playlist.PlaylistSongEntity;
import com.example.lumosonic.room.recent.RecentSongDao;
import com.example.lumosonic.room.recent.RecentSongEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import androidx.media3.common.C;

public class MusicService extends Service {

    public static final String TAG = "MusicService";
    public static final int NOTIFICATION_ID = 1; // 前台服务通知的唯一ID
    public static final String CHANNEL_ID = "Music PlayerChannel"; // 通知渠道ID
    private MediaSessionCompat mediaSession; // 媒体会话,防止锁屏时音乐停止
    private FavoriteSongDao favoriteSongDao;
    private RecentSongDao recentSongDao;
    private ExoPlayer exoPlayer; // 播放器,用于播放音乐
    private List<Song> songList; // 存储歌曲列表
    private int currentSongIndex = -1; // 当前播放歌曲的索引
    private volatile boolean isPlayingState = false; // 播放状态
    private volatile long currentPositionState = 0; // 当前播放位置
    private volatile long durationState = 0; // 歌曲总时长
    private int playMode = 0; // 0: 顺序播放, 1: 随机播放, 2: 循环播放
    public static final String ACTION_SCAN_MUSIC = "com.example.lumosonic.ACTION_SCAN_MUSIC"; // 扫描音乐的Action
    private AppDatabase database;
    private PlaylistSongDao playlistSongDao;
    private ExecutorService databaseExecutor; // 用于在后台线程执行数据库操作
    private List<Song> localSongsCache = null; // 用于缓存本地音乐列表，避免反复扫描
    public static final int PLAY_MODE_LIST = 0;
    public static final int PLAY_MODE_SHUFFLE = 1;
    public static final int PLAY_MODE_REPEAT = 2;

    // 使用RemoteCallbackList来安全地管理AIDL回调,它可以自动处理客户端进程死亡等情况
    // 每当有一个客户（比如一个Activity）绑定到你的MusicService并调用registerCallback(),
    // 它的回调接口实例就会被加入到mCallbacks这个列表中,通过notifySongChanged群发通知
    private final RemoteCallbackList<IMusicServiceCallback> mCallbacks = new RemoteCallbackList<>();

    // 创建一个Handler对象,用于在主线程更新UI
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    // 定义一个Runnable对象,用于更新当前播放位置
    private final Runnable updatePositionRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                currentPositionState = exoPlayer.getCurrentPosition();
                mainThreadHandler.postDelayed(this, 500);
            }
        }
    };
   /* 不是每个匿名函数都适合用lambda表达式,lambda表达式不能使用this等关键字
   private final Runnable updatePositionRunnable = () -> {
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            currentPositionState = exoPlayer.getCurrentPosition();
            // 使用 Runnable 变量名自身来代替 'this'
            mainThreadHandler.postDelayed(updatePositionRunnable, 500);
        }
    };*/

    @OptIn(markerClass = UnstableApi.class)
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        songList = new ArrayList<>();

        // 初始化数据库和后台执行器
        database = AppDatabase.getDatabase(this);
        playlistSongDao = database.playlistSongDao();
        favoriteSongDao = database.favoriteSongDao();
        recentSongDao = database.recentSongDao();

        databaseExecutor = Executors.newSingleThreadExecutor();

        // 从数据库加载持久化的播放列表
        loadPlaylistFromDb();

        // 用于设置ExoPlayer的音频属性
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA) // 用途是媒体播放
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC) // 内容是音乐
                .build();
        // 初始化ExoPlayer
        RenderersFactory renderersFactory =
                new DefaultRenderersFactory(this)
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
        exoPlayer = new ExoPlayer.Builder(this, renderersFactory).build();

        exoPlayer.setAudioAttributes(audioAttributes, true); // 设置音频属性

        exoPlayer.setWakeMode(C.WAKE_MODE_LOCAL);

        // 注册播放器监听器,启动或停止一个用于更新播放进度的任务,同时更新UI和通知栏
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                isPlayingState = isPlaying;
                if (isPlaying) {
                    // 立即向主线程的Handler提交一个updatePositionRunnable任务
                    mainThreadHandler.post(updatePositionRunnable);
                } else {
                    // 停止该任务,防止内存泄漏
                    mainThreadHandler.removeCallbacks(updatePositionRunnable);
                }
                updatePlaybackState(); // 更新播放状态
                updateNotification(); // 更新系统的通知栏
                notifyPlaybackStateChanged(isPlaying); // 通知所有注册的回调,更新播放状态
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    // 当歌曲真正准备好时,才执行这些重量级操作
                    durationState = exoPlayer.getDuration();
                    Song currentSong = songList.get(currentSongIndex);

                    // 更新元数据
                    updateMetadata(currentSong);

                    // 启动前台服务和创建通知
                    // startForeground(NOTIFICATION_ID, createNotification(currentSong));

                    // 通知 Activity 歌曲已改变
                    notifySongChanged(currentSong);
                }
                // 歌曲播放结束,选择播放模式
                if (playbackState == Player.STATE_ENDED) {
                    currentPositionState = 0;
                    handleSongCompletion();
                }
            }
            @Override
            public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                // 播放失败时的回调
                Log.e(TAG, "播放器错误: ", error);
                mainThreadHandler.post(() -> Toast.makeText(MusicService.this, songList.get(currentSongIndex).getTitle() + "播放失败, 已自动跳过", Toast.LENGTH_SHORT).show());
                // 自动播放下一首
                next();
            }
        });

        // 创建一个媒体会话,并设置回调
        mediaSession = new MediaSessionCompat(this, "LumoSonicMusicSession");

        // 设置回调,这样锁屏、通知栏的按钮点击才能生效
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { previous(); }
            @Override
            public void onSeekTo(long pos) {
                mainThreadHandler.post(() -> {
                    if (exoPlayer != null) exoPlayer.seekTo(pos); // 更新当前播放位置
                });
            }
        });
        // 设置媒体会话为活动状态
        mediaSession.setActive(true);

        //  new Thread(this::loadMusic).start();
        // 不用线程加载音乐,因为必须让他在获取权限之后在开始扫描

    }

    // AIDL的核心: 返回一个IBinder实例
    // 委托模式,户端（Activity）拿到了这个binder的代理对象（通常叫 iMusicService）后,
    // 它会像这样调用：iMusicService.play(0);
    private final IBinder binder = new IMusicService.Stub() {
        @Override
        public void play(int index) {
            MusicService.this.play(index);
        }

        @Override
        public void pause() {
            MusicService.this.pause();
        }

        @Override
        public void resume() {
            MusicService.this.resume();
        }

        @Override
        public void next() {
            MusicService.this.next();
        }

        @Override
        public void previous() {
            MusicService.this.previous();
        }

        @Override
        public boolean isPlaying() {
            return isPlayingState;
        }

        @Override
        public String getSongTitle() {
            if (currentSongIndex != -1) {
                return songList.get(currentSongIndex).getTitle();
            }
            return "沫雨";
        }
        @Override
        public String getSongArtist() {
            if (currentSongIndex != -1) {
                return songList.get(currentSongIndex).getArtist();
            }
            return "hqw";
        }

        @Override
        public int getDuration() {
            return (int) durationState;
        }


        @Override
        public int getCurrentPosition() {
            return (int) currentPositionState;
        }

        // 跳转到指定位置
        @Override
        public void seekTo(int position) {
            mainThreadHandler.post(() -> {
                if (exoPlayer != null) {
                    exoPlayer.seekTo(position);
                    currentPositionState = position;
                }
            });
        }

        // 获取当前播放歌曲的专辑图片的URI
        @Override
        public String getCurrentAlbumArtUri() {
            if (currentSongIndex != -1 && currentSongIndex < songList.size()) {
                return songList.get(currentSongIndex).getAlbumArtUri();
            }
            return null;
        }

        // 获取当前播放模式
        @Override
        public int getPlayMode() {
            return playMode;
        }

        // 设置播放模式
        @Override
        public void setPlayMode(int mode) {
            playMode = mode;
        }

        @Override
        public void registerCallback(IMusicServiceCallback cb) {
            if (cb != null) {
                mCallbacks.register(cb);  // 把新的订阅者(cb)添加到名册中
            }
        }

        @Override
        public void unregisterCallback(IMusicServiceCallback cb) {
            if (cb != null) {
                mCallbacks.unregister(cb); // 从名册中移除订阅者(cb)
            }
        }

        // 获取当前播放列表
        @Override
        public List<Song> getPlaylist() {
            return songList;
        }

        // 获取当前播放歌曲的索引
        @Override
        public int getCurrentIndex() {
            return currentSongIndex;
        }

        // 下载当前播放歌曲
        @Override
        public void downloadCurrentSong() {
            MusicService.this.downloadCurrentSong();
        }



        @Override
        public void toggleFavorite(Song song){
            if (song == null) return;
            // 在数据库中检查歌曲是否已收藏
            databaseExecutor.execute(() -> {
                // 使用countFavorite方法检查歌曲当前是否已收藏
                boolean isCurrentlyFavorite = favoriteSongDao.countFavorite(song.getId()) > 0;

                if (isCurrentlyFavorite) {
                    // 如果已收藏，则执行删除操作
                    favoriteSongDao.deleteFavoriteSongById(song.getId());
                    mainThreadHandler.post(() -> Toast.makeText(MusicService.this, "已从收藏中移除", Toast.LENGTH_SHORT).show());
                } else {
                    // 如果未收藏，则执行插入操作
                    FavoriteSongEntity entity = songToFavoriteEntity(song); // 使用新的转换方法
                    favoriteSongDao.insertFavoriteSong(entity);
                    mainThreadHandler.post(() -> Toast.makeText(MusicService.this, "已收藏", Toast.LENGTH_SHORT).show());
                }
            });
        }

        @Override
        public boolean isFavorite(long songId) {
            // AIDL 调用在 Binder 线程执行,可以直接进行数据库查询
            // 如果您的查询非常耗时,可以考虑异步回调,但对于简单的查询,直接返回即可
            return favoriteSongDao.countFavorite(songId) > 0;
        }

        @Override
        public List<Song> getFavoriteSongs() {
            List<FavoriteSongEntity> entities = favoriteSongDao.getAllFavoriteSongsBlocking();
            // 将 Entity 列表转换为 Song 列表
            return entities.stream()
                    .map(MusicService.this::favoriteEntityToSong) // 使用新的转换方法
                    .collect(Collectors.toList());
        }

        // 将新的网络歌曲添加到播放列表的开头
        @Override
        public void addAndPlaySong(Song song) {
            // 这是“单击”的逻辑：设为临时歌曲,添加到列表头部并播放
            song.setTemporary(true);
            if (songList == null) {
                songList = new ArrayList<>();
            }
            songList.add(0, song);
            // 播放这首歌（它现在是列表中的第一首,索引为0）
            play(0);
        }

        @Override
        public void addSongToPlaylist(Song song) {
            // 这是“长按”的逻辑：设为永久歌曲,添加到列表尾部并存入数据库
            song.setTemporary(false);
            if (songList == null) {
                songList = new ArrayList<>();
            }
            songList.add(song); // 添加到末尾
            // 存入数据库
            databaseExecutor.execute(() -> {
                PlaylistSongEntity entity = songToEntity(song);
                playlistSongDao.insertPlaylistSong(entity);
            });
            mainThreadHandler.post(() -> Toast.makeText(MusicService.this, song.getTitle() + " 已添加到播放列表", Toast.LENGTH_SHORT).show());
        }

        @Override
        public void removeSong(int index) {
            if (songList == null || index < 0 || index >= songList.size()) {
                return;
            }

            Song songToRemove = songList.get(index);
            // 从数据库中删除（如果是永久歌曲）
            if (!songToRemove.isTemporary()) {
                databaseExecutor.execute(() -> playlistSongDao.deletePlaylistSong(songToEntity(songToRemove)));
            }

            songList.remove(index);
            if (index == currentSongIndex) {
                exoPlayer.stop();
                // 因为列表项已移除,当前索引现在指向了下一首歌,所以直接play(index)即可
                play(index);
            } else if (index < currentSongIndex) {
                currentSongIndex--;
            }
        }
        @Override
        public List<Song> getLocalSongs() {
            // 如果缓存中已有数据,直接返回缓存,提高效率
            if (localSongsCache != null) {
                return localSongsCache;
            }
            // 如果没有缓存,则进行扫描。
            // AIDL 调用在 Binder 线程池中执行,不会阻塞 UI 线程,所以可以直接调用扫描方法
            localSongsCache = scanLocalSongsAndReturn();
            return localSongsCache;
        }

        // 设置播放列表并播放指定位置的歌曲
        @Override
        public void setPlaylistAndPlay(List<Song> playlist, int startIndex) {
            if (playlist == null || playlist.isEmpty() || startIndex < 0 || startIndex >= playlist.size()) {
                return;
            }
            // 使用主线程Handler来操作播放器和列表,确保线程安全
            mainThreadHandler.post(() -> {
                // 清空当前的播放列表
                MusicService.this.songList.clear();
                // 将新列表的所有歌曲设置为非临时（因为这是主播放列表）
                for (Song song : playlist) {
                    song.setTemporary(false);
                }
                // 将新列表添加到服务的播放列表中
                MusicService.this.songList.addAll(playlist);
                // 从用户点击的位置开始播放
                play(startIndex);
            });
        }
    };

    // 选择播放模式
    private void handleSongCompletion() {
        if (songList.isEmpty() || currentSongIndex < 0 || currentSongIndex >= songList.size()) {
            return;
        }

        Song finishedSong = songList.get(currentSongIndex);
        boolean wasTemporary = finishedSong.isTemporary();

        if (wasTemporary) {
            // 如果是临时歌曲,从列表中移除它
            songList.remove(currentSongIndex);

            if (songList.isEmpty()) {
                // 如果列表空了,停止播放
                exoPlayer.stop(); // 停止播放（不带参数）
                exoPlayer.clearMediaItems(); // 清空播放器的内部列表
                currentSongIndex = -1;

                // 当播放完全停止时,也应该停止前台服务并移除通知
                stopForeground(true);

                return;
            }
            // 播放下一首（即当前索引处的歌曲,因为前面的被删了）
            // 使用 %确保索引安全
            play(currentSongIndex % songList.size());
        } else {
            // 如果是永久歌曲,根据播放模式决定下一首
            if (playMode == PLAY_MODE_REPEAT) {
                exoPlayer.seekTo(0);
                exoPlayer.play();
            } else if (playMode == PLAY_MODE_SHUFFLE) {
                if (songList.size() > 1) {
                    int nextIndex;
                    do {
                        nextIndex = new Random().nextInt(songList.size());
                    } while (nextIndex == currentSongIndex);
                    play(nextIndex);
                } else {
                    play(0);
                }
            } else { // PLAY_MODE_LIST
                next(); // 顺序播放,直接调用next()
            }
        }
    }


    private void loadPlaylistFromDb() {
        databaseExecutor.execute(() -> {
            List<PlaylistSongEntity> entities = playlistSongDao.getAllPlaylistSongsBlocking(); // 假设你有一个阻塞版本的方法
            mainThreadHandler.post(() -> {
                songList.clear();
                // 使用 Stream API 转换列表
                songList.addAll(
                        entities.stream().map(this::entityToSong).collect(Collectors.toList())
                );
                Log.d(TAG, "从数据库加载了 " + songList.size() + " 首歌曲到播放列表。");
            });
        });
    }

    // 数据库转换方法,将 Song 转换为 PlaylistSongEntity
    private PlaylistSongEntity songToEntity(Song song) {
        return new PlaylistSongEntity(song.getId(), song.getTitle(), song.getArtist(), song.getData(), song.getDuration(), song.getAlbumArtUri());
    }

    // 数据库转换方法,将 PlaylistSongEntity 转换为 Song
    private Song entityToSong(PlaylistSongEntity entity) {
        Song song = new Song(entity.getId(), entity.getTitle(), entity.getArtist(), entity.getData(), entity.getDuration(), entity.getAlbumArtUri());
        song.setTemporary(false); // 从数据库加载的都是永久歌曲
        return song;
    }


    public MusicService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // onStartCommand是Service开始执行后台任务的入口点,接收并处理来自外部硬件
    // （如蓝牙耳机、线控耳机、车载系统、智能手表等）的媒体控制按键事件
    // 返回值决定了当系统因为内存不足而杀死 Service 后,应如何处理
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 外部（如通知栏）的播放控制指令可以在这里处理
        // 检查 Intent 是否是来自媒体按钮的广播
        if (intent != null && Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            MediaButtonReceiver.handleIntent(mediaSession, intent); // 媒体按钮处理
            return START_STICKY; // 处理完后保持服务运行
        }
       /*检查传递过来的 Intent 和它的 action
        if (intent != null && ACTION_SCAN_MUSIC.equals(intent.getAction())) {
            Log.d(TAG, "通过 onStartCommand 收到扫描命令，开始加载音乐...");
            new Thread(this::loadMusic).start();
        }*/

        // 保持 Service 的粘性行为
        return START_STICKY;
    }

    @SuppressLint("ForegroundServiceType") // 忽略前台服务类型的lint警告
    private void play(int index) {
        mainThreadHandler.post(() -> {
            if (songList == null || songList.isEmpty() || index < 0 || index >= songList.size()) {
                return;
            }
            Song songToPlay = songList.get(index);
            // 插入最近播放列表
            databaseExecutor.execute(() -> {
                RecentSongEntity recentEntity = songToRecentEntity(songToPlay);
                recentSongDao.insertRecentSong(recentEntity);
            });
            startForeground(NOTIFICATION_ID, createNotification(songToPlay)); // 创建并启动前台服务
            currentSongIndex = index;
            MediaItem mediaItem = MediaItem.fromUri(songToPlay.getData()); // 创建 MediaItem
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.play();
        });
    }

    // 暂停播放
    private void pause() {
        mainThreadHandler.post(() -> {
            if (exoPlayer != null) {
                exoPlayer.pause();
            }
        });
    }

    // 恢复播放
    private void resume() {
        mainThreadHandler.post(() -> {
            if (exoPlayer != null) {
                exoPlayer.play();
            }
        });
    }

    // 通知客户端当前播放的歌曲信息已改变
    private void notifySongChanged(Song song) {
        // 遍历所有注册的回调
        final int N = mCallbacks.beginBroadcast();
        // 遍历所有回调,通知客户端当前播放的歌曲信息已改变
        for (int i = 0; i < N; i++) {
            try {
                // 通知回调当前播放的歌曲信息已改变
                mCallbacks.getBroadcastItem(i).onSongChanged(song.getTitle(), song.getArtist(), song.getAlbumArtUri());
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing the dead callback.
            }
        }
        mCallbacks.finishBroadcast();
    }

    // 通知客户端播放状态已改变
    private void notifyPlaybackStateChanged(boolean isPlaying) {
        final int N = mCallbacks.beginBroadcast();
        for (int i = 0; i < N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).onPlaybackStateChanged(isPlaying);
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing the dead callback.
            }
        }
        mCallbacks.finishBroadcast();
    }

    // 播放下一首
    private void next() {
        if (songList.isEmpty()) return;
        currentSongIndex = (currentSongIndex + 1) % songList.size(); // 循环播放
        play(currentSongIndex);
    }

    // 播放上一首
    private void previous() {
        if (songList.isEmpty()) return;
        currentSongIndex = (currentSongIndex - 1 + songList.size()) % songList.size(); // 循环播放
        play(currentSongIndex);
    }


    // 加载音乐,通过ContentResolver去查询MediaStore这个数据库里的音乐信息

   /* MediaStore 的主要工作区域是共享存储空间，也就是用户可以自由访问的存储区域（通常被称为“内部存储”或 SD 卡）。它会重点扫描以下这些标准的公共目录：
    DCIM/: 这是存放由相机拍摄的照片和视频的最主要目录。(Environment.DIRECTORY_DCIM)
    Pictures/: 用于存放非相机拍摄的图片，例如截图 (Screenshots/ 子目录)、从其他应用保存的图片等。(Environment.DIRECTORY_PICTURES)
    Movies/: 用于存放电影、视频剪辑等。(Environment.DIRECTORY_MOVIES)
    Music/: 用于存放音乐文件。(Environment.DIRECTORY_MUSIC)
    Downloads/: 用户通过浏览器或其他应用下载的文件，如果是媒体文件，也会被扫描。(Environment.DIRECTORY_DOWNLOADS)
    Ringtones/, Alarms/, Notifications/: 分别用于存放铃声、闹钟和通知音。
    无论是手机自带的存储空间，还是用户插入的 SD 卡，只要上面有这些标准目录，MediaStore 都会去扫描它们。*/
   // 它现在不再修改 service 内部的 songList，而是扫描后直接返回结果。
   public List<Song> scanLocalSongsAndReturn() {
       // 创建一个全新的列表来存储扫描结果
       List<Song> localSongs = new ArrayList<>();

       String[] projection = {
               MediaStore.Audio.Media._ID,
               MediaStore.Audio.Media.TITLE,
               MediaStore.Audio.Media.ARTIST,
               MediaStore.Audio.Media.DATA,
               MediaStore.Audio.Media.DURATION,
               MediaStore.Audio.Media.ALBUM_ID
       };
       String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
       try (Cursor cursor = getContentResolver().query(
               MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null
       )) {
           if (cursor != null) {
               Log.d(TAG, "扫描到本地歌曲总数: " + cursor.getCount());
               while (cursor.moveToNext()) {
                   long id = cursor.getLong(0);
                   String title = cursor.getString(1);
                   String artist = cursor.getString(2);
                   String data = cursor.getString(3);
                   int duration = cursor.getInt(4);
                   long albumId = cursor.getLong(5);
                   Uri albumArtUri = ContentUris.withAppendedId(
                           Uri.parse("content://media/external/audio/albumart"), albumId
                   );
                   localSongs.add(new Song(id, title, artist, data, duration, albumArtUri.toString()));
               }
           }
       } catch (Exception e) {
           Log.e(TAG, "扫描本地音乐失败", e);
       }
       return localSongs;
   }

    // 创建通知渠道
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Player Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            // 将声音设置为 null
            serviceChannel.setSound(null, null);
            // VISIBILITY_PUBLIC表示在锁屏界面上会完整显示通知内容
            serviceChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            // 创建通知管理器,用于创建通知渠道
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    // 创建通知
    private Notification createNotification(Song song) {
        createNotificationChannel();
        // 点击通知时,跳转界面,如果界面已经打开,则直接跳转
        Intent notificationIntent = new Intent(this, MusicActivity.class);
        // 这是Activity启动模式的知识
        // 添加这两个Flag来控制Activity的启动行为,确保点击通知时,会打开指定Activity并清空任务栈
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // 同时修改PendingIntent的Flag,以确保如果Intent有附加数据,也能正确更新
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 为“上一首”按钮创建 PendingIntent
        PendingIntent prevPendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);

        // 为“播放/暂停”按钮创建 PendingIntent
        PendingIntent playPausePendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                PlaybackStateCompat.ACTION_PLAY_PAUSE);

        // 为“下一首”按钮创建 PendingIntent
        PendingIntent nextPendingIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(song.getTitle())
                .setContentText(song.getArtist())
                .setSmallIcon(R.drawable.tou_1)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 设置通知可见性为PUBLIC
                // 上一首按钮
                .addAction(new NotificationCompat.Action(R.drawable.ic_skip_previous, "Previous", prevPendingIntent))
                // 播放/暂停按钮
                .addAction(new NotificationCompat.Action(
                        isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play, // 根据播放状态显示不同图标
                        "Play/Pause", // 按钮文字
                        playPausePendingIntent))
                // 下一首按钮
                .addAction(new NotificationCompat.Action(R.drawable.ic_skip_next, "Next", nextPendingIntent))

                // 关联 MediaStyle 和 MediaSession
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2) // 紧凑视图下显示所有三个按钮
                )
                .build();
    }

    public boolean isPlaying() {
        // 确保它也从ExoPlayer获取状态
        return this.exoPlayer != null && this.exoPlayer.isPlaying();
    }

    // 更新通知
    private void updateNotification() {
        // 检查当前播放的索引是否有效,如果无效则不更新通知
        if (currentSongIndex != -1 && currentSongIndex < songList.size()) {
            Notification notification = createNotification(songList.get(currentSongIndex));
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void downloadCurrentSong() {
        if ((currentSongIndex != -1) && (songList == null) || songList.isEmpty()) {
            Log.d(TAG, "当前没有歌曲可以下载");
            return;
        }

        Song currentSong = songList.get(currentSongIndex);
        String songUrl = currentSong.getData();
        String songName = currentSong.getTitle();

        // DownloadManger只能处理网络URL,不能处理本地文件路径
        // 我们需要判断一下songUrl是不是一个网络地址
        if (songUrl == null || (!songUrl.startsWith("http://")&& !songUrl.startsWith("https://"))){
            mainThreadHandler.post(() -> {
                // 如果是本地文件,提示用户文件已存在
                if (songUrl != null && !songUrl.startsWith("content://")) {
                    Toast.makeText(this, "文件已在本地", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "这不是一个可下载的网络文件", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        // 创建下载请求
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(songUrl));

        // 配置请求参数,设置通知栏的可见性,标题和描述
        request.setTitle(songName);
        request.setDescription("正在下载" + currentSong.getArtist());
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        // 设置下载的目标位置
        // Environment.DIRECTORY_MUSIC是公共的音乐文件夹
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, songName + ".mp3");

        // 允许在移动网络和WiFi下都进行
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);

        // 获取DownloadManager系统服务
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            downloadManager.enqueue(request); // 加入下载队列
        } else {
            mainThreadHandler.post(() -> Toast.makeText(this, "下载失败", Toast.LENGTH_SHORT).show());
        }
    }

    private RecentSongEntity songToRecentEntity(Song song) {
        return new RecentSongEntity(
                song.getId(), song.getTitle(), song.getArtist(),
                song.getData(), song.getDuration(), song.getAlbumArtUri(),
                System.currentTimeMillis() // 使用当前时间作为时间戳
        );
    }

    private FavoriteSongEntity songToFavoriteEntity(Song song) {
        return new FavoriteSongEntity(
                song.getId(), song.getTitle(), song.getArtist(),
                song.getData(), song.getDuration(), song.getAlbumArtUri(), true
        );
    }

    private Song favoriteEntityToSong(FavoriteSongEntity entity) {
        Song song = new Song(
                entity.getId(), entity.getTitle(), entity.getArtist(),
                entity.getData(), entity.getDuration(), entity.getAlbumArtUri()
        );
        song.setTemporary(false); // 从数据库加载的都不是临时歌曲
        return song;
    }

    // 更新媒体会话的元数据,用于显示在通知栏中
    private void updateMetadata(Song song) {
        if (song == null || mediaSession == null) return;
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtist())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, exoPlayer.getDuration())
                .build();
        mediaSession.setMetadata(metadata);
    }

    // 更新媒体会话的播放状态,用于显示在通知栏中
    private void updatePlaybackState() {
        if (mediaSession == null || exoPlayer == null) return;
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SEEK_TO
                );
        // 从 ExoPlayer 获取状态
        int state = (exoPlayer.isPlaying()) ?
                PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        long position = exoPlayer.getCurrentPosition();

        stateBuilder.setState(state, position, 1.0f); // 播放状态,当前位置,播放速度
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release(); // 释放播放器
            exoPlayer = null;
        }
        if (mediaSession != null) {
            mediaSession.release(); // 释放媒体会话
        }
        if (databaseExecutor != null) {
            databaseExecutor.shutdown(); // 关闭数据库线程池
        }
    }
}