package com.example.lumosonic.list;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.example.lumosonic.R;
import com.example.lumosonic.ipc.Song;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;


// 连接复杂数据和视图组件,负责创建视图、绑定数据,实现动态列表界面
public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.SongViewHolder> {
    private final List<Song> songList;
    private final Context context;
    private final OnSongClickListener clickListener; // 点击事件回调接口
    private final OnSongLongClickListener longClickListener;
    private int currentPlayingIndex = -1; // 当前播放歌曲的索引


    // 定义点击事件的回调接口
    /* 任何想要处理我这个列表点击事件的类,都必须实现 OnSongClickListener 这个接口,
       并且必须提供一个叫做 onSongClick 的具体方法,这个方法会接收一个 int 类型的参数
       （也就是被点击项的位置）*/
    public interface OnSongLongClickListener {
        void onSongLongClick(int position);
    }

    public interface OnSongClickListener {
        void onSongClick(int position);
    }

    public PlaylistAdapter(Context context, List<Song> songList, OnSongClickListener clickListener, OnSongLongClickListener longClickListener) {
        this.context = context;
        this.songList = songList;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_song, parent, false);
        return new SongViewHolder(view);
    }

    // 绑定数据到视图
    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songList.get(position);
        holder.title.setText(song.getTitle());
        holder.artist.setText(song.getArtist());

        Glide.with(context)
                .load(song.getAlbumArtUri())
                .placeholder(R.drawable.ic_album_placeholder)
                .error(R.drawable.tou_1)
                .into(holder.albumArt);

        // 根据是否为当前播放歌曲,显示或隐藏指示器
        if (position == currentPlayingIndex) {
            holder.playingIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.playingIndicator.setVisibility(View.GONE);
        }

        // 设置单击事件
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onSongClick(holder.getAdapterPosition()); // 索引为参数传递给回调方法
            }
        });

        // 设置长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onSongLongClick(holder.getAdapterPosition());
                return true; // 表示事件已被消费,不会再触发单击事件
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    // 更新当前播放歌曲的索引并刷新列表
    public void setCurrentPlayingIndex(int index) {
        int previousIndex = this.currentPlayingIndex;
        this.currentPlayingIndex = index;
        if (previousIndex != -1) {
            notifyItemChanged(previousIndex);
        }
        if (currentPlayingIndex != -1) {
            notifyItemChanged(currentPlayingIndex);
        }
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView albumArt;
        TextView title;
        TextView artist;
        LottieAnimationView playingIndicator;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            albumArt = itemView.findViewById(R.id.image_album_art_small);
            title = itemView.findViewById(R.id.text_song_title_item);
            artist = itemView.findViewById(R.id.text_artist_item);
            playingIndicator = itemView.findViewById(R.id.lottiePlayView);
        }
    }
}
