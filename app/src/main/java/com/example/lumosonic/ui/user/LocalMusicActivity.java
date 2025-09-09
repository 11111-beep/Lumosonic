package com.example.lumosonic.ui.user;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lumosonic.R;
import com.example.lumosonic.ipc.IMusicService;
import com.example.lumosonic.ipc.MusicActivity;
import com.example.lumosonic.ipc.MusicService;
import com.example.lumosonic.ipc.Song;
import com.example.lumosonic.list.PlaylistAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import soup.neumorphism.NeumorphImageButton;

public class LocalMusicActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PlaylistAdapter adapter;
    private List<Song> localSongList = new ArrayList<>();
    private IMusicService musicService;
    private boolean isBound = false;

    // 用于在后台线程获取歌曲列表
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = IMusicService.Stub.asInterface(service);
            isBound = true;
            // 服务连接成功后，加载本地音乐
            loadLocalSongs();
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
        setContentView(R.layout.activity_local_music);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setupTitleBar();
        setupRecyclerView();
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setupTitleBar() {
        View titleBar = findViewById(R.id.title_bar);
        TextView titleText = titleBar.findViewById(R.id.title_text);
        NeumorphImageButton backButton = titleBar.findViewById(R.id.button_back);
        NeumorphImageButton downloadButton = titleBar.findViewById(R.id.button_download);

        titleText.setText("本地音乐");
        titleText.setVisibility(View.VISIBLE);
        downloadButton.setVisibility(View.GONE);
        backButton.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_view_local_music);
        adapter = new PlaylistAdapter(this, localSongList,
                position -> {
                    if (isBound && musicService != null) {
                        try {
                            // 使用 addAndPlaySong 实现临时播放
                            musicService.addAndPlaySong(localSongList.get(position));
                            Intent intent = new Intent(this, MusicActivity.class);
                            intent.putExtra("song", localSongList.get(position));
                            startActivity(intent);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                },
                position -> {
                    if (isBound && musicService != null) {
                        try {
                            musicService.addSongToPlaylist(localSongList.get(position));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                });
        recyclerView.setAdapter(adapter);
    }

    private void loadLocalSongs() {
        if (!isBound || musicService == null) return;

        // 在后台线程中调用服务方法,防止因扫描时间过长而卡顿
        executor.execute(() -> {
            try {
                final List<Song> songs = musicService.getLocalSongs();
                // 获取到数据后,切换回主线程更新UI
                handler.post(() -> {
                    localSongList.clear();
                    localSongList.addAll(songs);
                    adapter.notifyDataSetChanged();
                });
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });
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