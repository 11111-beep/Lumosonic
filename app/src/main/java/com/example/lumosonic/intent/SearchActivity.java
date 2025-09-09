package com.example.lumosonic.intent;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lumosonic.ipc.MusicActivity;
import com.example.lumosonic.R;
import com.example.lumosonic.ipc.IMusicService;
import com.example.lumosonic.ipc.MusicService;
import com.example.lumosonic.ipc.Song;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import soup.neumorphism.NeumorphImageButton;

public class SearchActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private SearchResultAdapter adapter;
    private List<SongInfo> searchResults = new ArrayList<>(); // 存储搜索结果
    private MusicApiManager musicApiManager; // 用于管理歌曲API网络请求

    private IMusicService musicService;
    private boolean isBound = false;
    private NeumorphImageButton btnback;
    private NeumorphImageButton download;

    private TextView titleText;

    private com.airbnb.lottie.LottieAnimationView lottieLoading;
    private TextView textNoResults;

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
        setContentView(R.layout.activity_search);
        // 沉浸式状态栏
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // 绑定服务
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE); // 绑定服务并自动创建

        lottieLoading = findViewById(R.id.lottie_loading);
        textNoResults = findViewById(R.id.text_no_results);

        musicApiManager = new MusicApiManager();
        setupRecyclerView();

        // 获取从HomeFragment传来的关键字
        String keyword = getIntent().getStringExtra("SEARCH_KEYWORD");
        if (keyword != null && !keyword.isEmpty()) {
            performSearch(keyword);
        } else {
            Toast.makeText(this, "没有收到搜索关键字", Toast.LENGTH_SHORT).show();
            lottieLoading.setVisibility(View.GONE);
            textNoResults.setText("请输入关键字");
            textNoResults.setVisibility(View.VISIBLE);
        }
        btnback = findViewById(R.id.button_back);
        btnback.setOnClickListener(v -> finish());
        download = findViewById(R.id.button_download);
        download.setVisibility(View.GONE);
        titleText = findViewById(R.id.title_text);
        titleText.setVisibility(View.VISIBLE);
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_view_search);

        // 这个songInfo就是正确类型的参数
        adapter = new SearchResultAdapter(this, searchResults, songInfo -> {
            // 处理列表项点击事件
            if (!isBound || musicService == null) {
                Toast.makeText(this, "播放服务未连接", Toast.LENGTH_SHORT).show();
                return;
            }
            // 直接使用lambda表达式返回的songInfo对象
            playOnlineSong(songInfo);
        }, // 长按事件的实现
                songInfo -> {
                    if (!isBound || musicService == null) {
                        Toast.makeText(this, "播放服务未连接", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addSongToPersistentPlaylist(songInfo);
                },
                musicApiManager
        );
        recyclerView.setAdapter(adapter);
    }

    // 单击时调用：临时播放
    private void playOnlineSong(SongInfo songInfo) {
        musicApiManager.fetchSongAndAlbumUrls(songInfo, "netease", (songUrl, albumArtUrl) -> {
            if (songUrl != null) {
                String artist = String.join(", ", songInfo.getArtist());
                Song onlineSong = new Song(songInfo.getId(), songInfo.getName(), artist, songUrl, 0, albumArtUrl);

                try {
                    // 调用 Service 的临时播放方法
                    musicService.addAndPlaySong(onlineSong);
                    Intent intent = new Intent(SearchActivity.this, MusicActivity.class);
                    intent.putExtra("SELECTED_SONG", onlineSong);
                    startActivity(intent);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "播放失败", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "获取歌曲链接失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 长按时调用：永久添加到播放列表
    private void addSongToPersistentPlaylist(SongInfo songInfo) {
        musicApiManager.fetchSongAndAlbumUrls(songInfo, "netease", (songUrl, albumArtUrl) -> {
            if (songUrl != null) {
                String artist = String.join(", ", songInfo.getArtist());
                Song onlineSong = new Song(songInfo.getId(), songInfo.getName(), artist, songUrl, 0, albumArtUrl);

                try {
                    // 调用Service的永久添加方法
                    musicService.addSongToPlaylist(onlineSong);
                    // 长按只添加,不会跳转页面
                } catch (RemoteException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "添加失败", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "获取歌曲信息失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performSearch(String keyword) {

        lottieLoading.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        textNoResults.setVisibility(View.GONE);

        // searchSongs返回了一个Call对象
        musicApiManager.searchSongs(keyword, "netease").enqueue(new Callback<List<SongInfo>>() {
            @Override
            public void onResponse(Call<List<SongInfo>> call, Response<List<SongInfo>> response) {
                // 请求结束后,立刻隐藏加载动画
                lottieLoading.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    // 如果成功且有数据,显示列表
                    recyclerView.setVisibility(View.VISIBLE);
                    searchResults.clear();
                    searchResults.addAll(response.body());
                    adapter.notifyDataSetChanged();
                } else {
                    // 如果失败或无数据,显示提示文本
                    textNoResults.setText("未能找到相关歌曲");
                    textNoResults.setVisibility(View.VISIBLE);
                    Toast.makeText(SearchActivity.this, "搜索无结果", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<SongInfo>> call, Throwable t) {
                lottieLoading.setVisibility(View.GONE);

                // 显示错误提示
                textNoResults.setText("网络错误，请稍后重试");
                textNoResults.setVisibility(View.VISIBLE);
                Toast.makeText(SearchActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 解绑服务
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }
}