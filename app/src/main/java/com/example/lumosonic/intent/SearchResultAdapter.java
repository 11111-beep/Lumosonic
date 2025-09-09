package com.example.lumosonic.intent;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.lumosonic.R;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(SongInfo songInfo);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(SongInfo songInfo);
    }

    private final Context context;
    private final List<SongInfo> songInfoList;

    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;
    private final MusicApiManager musicApiManager; // 用于获取歌曲详情和专辑图片

    // 创建一个Map作为我们的内存缓存
    // Key是picId,Value是获取到的图片URL
    private final Map<String, String> albumArtCache = new HashMap<>();
    public SearchResultAdapter(Context context, List<SongInfo> songInfoList,
                               OnItemClickListener clickListener,
                               OnItemLongClickListener longClickListener,
                               MusicApiManager musicApiManager) {
        this.context = context;
        this.songInfoList = songInfoList;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        this.musicApiManager = musicApiManager;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SongInfo song = songInfoList.get(position);
        holder.title.setText(song.getName());
        holder.artist.setText(String.join(", ", song.getArtist()));
        holder.playingIndicator.setVisibility(View.GONE);
        // 点击事件的逻辑会在SearchActivity中处理

        // 绑定单击事件
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onItemClick(song);
            }
        });

        // 绑定长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(song);
                return true; // 返回 true 表示事件已消费,不会再触发单击事件
            }
            return false;
        });
        // 由于视图复用,需要先清除上一个item可能留下的图片,防止闪烁或显示错乱
        holder.albumArt.setImageResource(R.drawable.ic_album_placeholder);

        final String picId = song.getPicId();

        // 检查缓存中是否已有URL
        if (albumArtCache.containsKey(picId)) {
            // 如果缓存命中 (Hit)：直接从缓存取URL并加载图片,不发起网络请求
            String cachedUrl = albumArtCache.get(picId);
            if (cachedUrl != null) {
                Glide.with(context)
                        .load(cachedUrl)
                        .thumbnail(0.25f) // 先加载一个25%大小的缩略图
                        .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade()) // 添加淡入淡出动画
                        .placeholder(R.drawable.ic_album_placeholder)
                        .error(R.drawable.tou_1)
                        .into(holder.albumArt);
            }
        } else {
            // 如果缓存未命中 (Miss)：才发起网络请求
            if (!TextUtils.isEmpty(picId)) {
                musicApiManager.fetchAlbumArtUrl(picId, "netease", url -> {
                    // 当URL获取成功后,这个回调会被执行

                    // 在执行任何UI操作前,检查Context对应的Activity是否还存活
                    if (context instanceof android.app.Activity) {
                        android.app.Activity activity = (android.app.Activity) context;
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            // 如果Activity正在关闭或已经被销毁,则不执行任何UI操作,直接返回
                            return;
                        }
                    }

                    // 将获取到的新URL存入缓存,供下次使用
                    if (url != null) {
                        albumArtCache.put(picId, url);
                    }

                    // 检查holder是否已被复用,如果还在显示当前位置,就加载图片
                    // 这里的检查依然是必要的,防止图片错位
                    if (holder.getAdapterPosition() == position) {
                        // 经过上面的检查,可以确保这里的 context 是有效的
                        Glide.with(context)
                                .load(url)
                                .thumbnail(0.25f) // 先加载一个25%大小的缩略图
                                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade()) // 添加淡入淡出动画
                                .placeholder(R.drawable.ic_album_placeholder)
                                .error(R.drawable.tou_1)
                                .into(holder.albumArt);
                    }
                });
            }
        }
    }

    @Override
    public int getItemCount() {
        return songInfoList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView albumArt;
        TextView title;
        TextView artist;
        View playingIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            albumArt = itemView.findViewById(R.id.image_album_art_small);
            title = itemView.findViewById(R.id.text_song_title_item);
            artist = itemView.findViewById(R.id.text_artist_item);
            playingIndicator = itemView.findViewById(R.id.lottiePlayView);
        }
    }
}