package com.example.lumosonic.ui.user;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
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
import com.example.lumosonic.room.favorite.FavoriteSongDao;
import com.example.lumosonic.room.favorite.FavoriteSongEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import soup.neumorphism.NeumorphImageButton;

public class FavoriteMusicActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PlaylistAdapter adapter;
    private List<Song> favoriteSongList = new ArrayList<>();
    private FavoriteSongDao favoriteSongDao;

    private IMusicService musicService;
    private boolean isBound = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = IMusicService.Stub.asInterface(service);
            isBound = true;
            Log.d("FavoriteMusicActivity", "MusicService connected.");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
            isBound = false;
            Log.d("FavoriteMusicActivity", "MusicService disconnected.");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_music);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setupTitleBar();
        initViews(); // 初始化RecyclerView和Adapter

        // 获取数据库实例和 DAO
        AppDatabase db = AppDatabase.getDatabase(this);
        favoriteSongDao = db.favoriteSongDao();

        // 从数据库加载收藏的歌曲
        loadFavoriteSongs();

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view_favorite_music);

        adapter = new PlaylistAdapter(this, favoriteSongList,
                // 单击事件：将整个收藏列表作为播放列表进行播放
                position -> {
                    if (isBound && musicService != null) {
                        try {
                            // 将当前“我喜欢”列表设置为播放列表,并从点击的位置开始播放
                            musicService.setPlaylistAndPlay(favoriteSongList, position);
                            // 跳转到播放详情页
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
                            musicService.addSongToPlaylist(favoriteSongList.get(position));
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

        titleText.setText("我喜欢");
        titleText.setVisibility(View.VISIBLE);
        downloadButton.setVisibility(View.GONE);
        backButton.setOnClickListener(v -> finish());
    }

    private void loadFavoriteSongs() {
        // 使用后台线程执行数据库查询
        executor.execute(() -> {
            List<FavoriteSongEntity> favoriteEntities = favoriteSongDao.getAllFavoriteSongsBlocking();
            List<Song> songs = new ArrayList<>();
            for (FavoriteSongEntity entity : favoriteEntities) {
                songs.add(new Song(
                        entity.getId(),
                        entity.getTitle(),
                        entity.getArtist(),
                        entity.getData(),
                        entity.getDuration(),
                        entity.getAlbumArtUri()
                ));
            }

            // 回到主线程更新UI
            runOnUiThread(() -> {
                favoriteSongList.clear();
                favoriteSongList.addAll(songs);
                adapter.notifyDataSetChanged();
            });
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