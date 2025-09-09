package com.example.lumosonic.intent;

import com.google.gson.annotations.SerializedName;

// https://api.music.163.com/api/song/detail?id=<id>&ids=[<id>]歌曲URL
public class SongUrl {
    @SerializedName("url")
    private String url;

    @SerializedName("br")
    private double bitRate;

    public String getUrl() {
        return url;
    }

    public double getBitRate() {
        return bitRate;
    }
}
