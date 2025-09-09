package com.example.lumosonic.list;

import android.os.Bundle;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lumosonic.R;
import com.example.lumosonic.ipc.IMusicService;
import com.example.lumosonic.ipc.Song;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

// 展示当前的播放列表和处理点击事件
public class PlaylistBottomSheetFragment extends BottomSheetDialogFragment {

    private IMusicService musicService;
    private List<Song> songList = new ArrayList<>(); // 从Service获取的播放列表
    private PlaylistAdapter adapter;

    // 工厂方法,用于传递MusicService的Binder代理
    public static PlaylistBottomSheetFragment newInstance() {
        return new PlaylistBottomSheetFragment();
    }

    // 允许Activity设置MusicService实例
    public void setMusicService(IMusicService service) {
        this.musicService = service;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist_bottom_sheet, container, false);

        // 加载播放列表数据
        loadPlaylist();

        RecyclerView recyclerView = view.findViewById(R.id.recycler_view_playlist);
        adapter = new PlaylistAdapter(getContext(), songList,
                // 单击事件的实现
                position -> {
                    try {
                        if (musicService != null) {
                            musicService.play(position);
                            dismiss();
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                },
                // 长按事件的实现
                position -> {
                    // 为了防止误操作,弹出一个确认对话框
                    new AlertDialog.Builder(requireContext())
                            .setTitle("移除歌曲")
                            .setMessage("确定要从播放列表中移除这首歌曲吗？")
                            .setPositiveButton("确定", (dialog, which) -> {
                                removeSongAtPosition(position);
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }
        );
        recyclerView.setAdapter(adapter);

        // 同步当前正在播放的歌曲
        updateCurrentPlaying();
        return view;
    }

    // 从MusicService加载播放列表
    private void loadPlaylist() {
        if (musicService != null) {
            try {
                // 注意：这里需要一个从Service获取整个播放列表的方法
                List<Song> playlistFromService = musicService.getPlaylist();
                if (playlistFromService != null) {
                    songList.clear();
                    songList.addAll(playlistFromService);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    // 更新当前播放的歌曲高亮
    private void updateCurrentPlaying() {
        if (musicService != null && adapter != null) {
            try {
                int currentIndex = musicService.getCurrentIndex();
                adapter.setCurrentPlayingIndex(currentIndex);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
    private void removeSongAtPosition(int position) {
        if (musicService == null || position < 0 || position >= songList.size()) {
            return;
        }
        try {
            // 通知Service删除歌曲
            musicService.removeSong(position);
            // 更新Fragment的本地数据列表,保持与 Service 同步
            songList.remove(position);
            // 通知Adapter某一项已被移除
            adapter.notifyItemRemoved(position);
            // 为了确保后续项目的索引正确,刷新一下变化范围
            adapter.notifyItemRangeChanged(position, songList.size());
            // 重新同步一下当前播放的高亮状态
            updateCurrentPlaying();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}