package com.example.lumosonic.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;



// 不同Activity之间不能“直接”传递对象
// 让Song对象能够在 Android 的不同组件（如 Activity、Service）之间进行高效传递
public class Song implements Parcelable {
    private final long id;
    private final String title;
    private final String artist;
    private final String data; // 文件路径
    private final long duration; // 区间
    private String albumArtUri; // 专辑封面URI
    private boolean isTemporary = false; // 判断是否为临时歌曲的标志

    private boolean isFavorite = false; // 判断是否为收藏歌曲的标志

    public Song(long id, String title, String artist, String data, long duration, String albumArtUri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.data = data;
        this.duration = duration;
        this.albumArtUri = albumArtUri;
    }

    // 解包过程
    protected Song(@NonNull Parcel in) {
        id = in.readLong();
        title = in.readString();
        artist = in.readString();
        data = in.readString();
        duration = in.readLong();
        albumArtUri = in.readString();
        isTemporary = in.readByte() != 0;
        isFavorite = in.readByte() != 0;
    }

    // 读取Parcel中的数据来还原Song对象的工厂方法
    public static final Creator<Song> CREATOR = new Creator<Song>() {
        @Override
        public Song createFromParcel(Parcel in) {
            return new Song(in);
        }

        @Override
        public Song[] newArray(int size) {
            return new Song[size];
        }
    };

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getData() { return data; }
    public long getDuration() { return duration; }
    public String getAlbumArtUri() { return albumArtUri; }
    public boolean isTemporary() { return isTemporary; }
    public void setTemporary(boolean temporary) { isTemporary = temporary; }
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    @Override
    public int describeContents() {
        return 0;
    }

    public void setAlbumArtUri(String albumArtUri) {
        this.albumArtUri = albumArtUri;
    }
    // 打包过程
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(artist);
        dest.writeString(data);
        dest.writeLong(duration);
        dest.writeString(albumArtUri);
        dest.writeByte((byte) (isTemporary ? 1 : 0));
        dest.writeByte((byte) (isFavorite ? 1 : 0));
    }

    @NonNull
    @Override
    public String toString() {
        return "Song{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                ", data='" + data + '\'' +
                ", duration=" + duration + '\'' +
                ", albumArtUri='" + albumArtUri + '\'' +
                ", isTemporary=" + isTemporary + '\'' +
                ", isFavorite=" + isFavorite +
                '}';
    }
}
