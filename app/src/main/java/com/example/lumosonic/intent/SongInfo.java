package com.example.lumosonic.intent;

import com.google.gson.annotations.SerializedName;

import java.util.List;

// https://api.deezer.com/track/<id>歌曲信息
public class SongInfo {

    @SerializedName("id")
    private long id; // 曲目ID

    @SerializedName("name")
    private String name; // 歌曲名

    @SerializedName("artist")
    private List<String> artist; // 歌手列表

    @SerializedName("pic_id")
    private String picId; // 专辑图ID

    // --- Getters ---
    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getArtist() {
        return artist;
    }

    public String getPicId() {
        return picId;
    }

    @Override
    public String toString() {
        return "SongInfo{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", artist=" + artist +
                ", picId='" + picId + '\'' +
                '}';
    }

}
