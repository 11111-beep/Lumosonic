package com.example.lumosonic.ui.home;

import android.graphics.Bitmap;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class HomeViewModel extends ViewModel {

    private static final String TAG = "HomeViewModel";

    // 持有专辑封面URI的LiveData
    private final MutableLiveData<String> albumArtUri = new MutableLiveData<>();
    // 持有播放状态的LiveData
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>();
    // 获取专辑封面URI的LiveData
    public LiveData<String> getAlbumArtUri() {
        return albumArtUri;
    }
    // 获取播放状态的LiveData
    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }

    // 由Fragment调用,当从Service收到歌曲变化的回调时,更新专辑封面URI
    public void onSongChanged(String newAlbumArtUri) {
        albumArtUri.postValue(newAlbumArtUri); // 新的地址安全地更新到albumArtUri这个LiveData对象中
    }

    // 由Fragment调用,当从Service收到播放状态变化的回调时,更新播放状态
    public void onPlaybackStateChanged(boolean playing) {
        isPlaying.postValue(playing); // 新的状态安全地更新到isPlaying这个LiveData对象中
    }

}