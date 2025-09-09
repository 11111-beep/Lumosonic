package com.example.lumosonic.intent;

import com.google.gson.annotations.SerializedName;

// https://api.deezer.com/album/<id>专辑封面
public class AlbumArt {

    @SerializedName("url")
    private String url;

    public String getUrl() {
        return url;
    }
}
