package com.example.lumosonic.ui.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lumosonic.R;
import com.example.lumosonic.ipc.IMusicService;
import com.example.lumosonic.ipc.MusicActivity;
import com.example.lumosonic.ipc.MusicService;
import com.example.lumosonic.ipc.Song;
import com.example.lumosonic.list.PlaylistAdapter;
import com.example.lumosonic.room.AppDatabase;
import com.example.lumosonic.room.recent.RecentSongDao;
import com.example.lumosonic.room.recent.RecentSongEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import soup.neumorphism.NeumorphImageButton;

public class RecentMusicActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PlaylistAdapter adapter;
    private List<Song> recentSongList = new ArrayList<>();
    private RecentSongDao recentSongDao;

    // 与 MusicService相关的变量
    private IMusicService musicService;
    private boolean isBound = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ServiceConnection用于连接服务
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = IMusicService.Stub.asInterface(service);
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 使用你的布局文件
        setContentView(R.layout.activity_recent_music);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setupTitleBar();
        initViews();

        // 获取数据库实例和DAO
        AppDatabase db = AppDatabase.getDatabase(this);
        recentSongDao = db.recentSongDao();

        // 从数据库加载最近播放的歌曲
        loadRecentSongs();

        // 绑定到MusicService
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view_recent_music);

        adapter = new PlaylistAdapter(this, recentSongList,
                // 单击事件：将整个最近播放列表作为播放列表进行播放
                position -> {
                    if (isBound && musicService != null) {
                        try {
                            musicService.addAndPlaySong(recentSongList.get(position));
                            Intent intent = new Intent(this, MusicActivity.class);
                            startActivity(intent);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                },
                // 长按事件：添加到当前播放列表
                position -> {
                    if (isBound && musicService != null) {
                        try {
                            musicService.addSongToPlaylist(recentSongList.get(position));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupTitleBar() {
        View titleBar = findViewById(R.id.title_bar);
        TextView titleText = titleBar.findViewById(R.id.title_text);
        NeumorphImageButton backButton = titleBar.findViewById(R.id.button_back);
        NeumorphImageButton downloadButton = titleBar.findViewById(R.id.button_download);

        titleText.setText("最近播放");
        downloadButton.setVisibility(View.GONE);
        backButton.setOnClickListener(v -> finish());
    }

    private void loadRecentSongs() {
        executor.execute(() -> {
            List<RecentSongEntity> recentEntities = recentSongDao.getRecentSongs();
            List<Song> songs = new ArrayList<>();
            for (RecentSongEntity entity : recentEntities) {
                songs.add(recentEntityToSong(entity));
            }

            // 回到主线程更新 UI
            runOnUiThread(() -> {
                recentSongList.clear();
                recentSongList.addAll(songs);
                adapter.notifyDataSetChanged();
            });
        });
    }

    // 将 RecentSongEntity 转换为 Song 的辅助方法
    private Song recentEntityToSong(RecentSongEntity entity) {
        return new Song(
                entity.getId(),
                entity.getTitle(),
                entity.getArtist(),
                entity.getData(),
                entity.getDuration(),
                entity.getAlbumArtUri()
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }
}