package com.example.lumosonic.intent;

import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MusicApiManager {

    //赋值操作的右边 (RetrofitClient.getService()) 返回的不是接口本身,
    // 而是一个在运行时由 Retrofit 生成的、实现了该接口的代理类的实例
    private final MusicApiService apiService = RetrofitClient.getService();

    // 搜索歌曲
    public Call<List<SongInfo>> searchSongs(String keyword, String source) {
        return apiService.searchSongs(keyword, source);
    }
    // 定义一个新的回调接口
    public interface OnSongDetailsFetched {
        void onDetailsFetched(String songUrl, String albumArtUrl);
    }

    // 并行启动两个独立的异步任务（获取歌曲URL和获取专辑图URL）,并在它们都完成后调用回调
    // 这两个任务是并行进行的,互不干扰,互不等待
    public void fetchSongAndAlbumUrls(SongInfo songInfo, String source, OnSongDetailsFetched callback) {
        // 定义我们想要尝试的音质列表（从高到低）
        final int[] bitratesToTry = {999, 740, 320, 192, 128};

        // 存储最终结果
        final String[] songUrlResult = {null};
        final String[] albumArtUrlResult = {null};
        final int[] completedCalls = {0};

        // 开始递归尝试获取歌曲URL,从数组的第一个元素（索引0）开始
        tryFetchingSongUrlRecursive(songInfo, source, bitratesToTry, 0, url -> {
            // 这是递归结束后的最终回调,onUrlFetched方法的实现体
            songUrlResult[0] = url;
            completedCalls[0]++;
            if (completedCalls[0] == 2) {
                callback.onDetailsFetched(songUrlResult[0], albumArtUrlResult[0]);
            }
        });
        // 获取专辑图URL
        apiService.getAlbumArt(songInfo.getPicId(), source, 500).enqueue(new Callback<AlbumArt>() {
            @Override
            public void onResponse(Call<AlbumArt> call, Response<AlbumArt> response) {
                if (response.isSuccessful() && response.body() != null) {
                    albumArtUrlResult[0] = response.body().getUrl();
                }
                completedCalls[0]++;
                if (completedCalls[0] == 2) {
                    callback.onDetailsFetched(songUrlResult[0], albumArtUrlResult[0]);
                }
            }

            @Override
            public void onFailure(Call<AlbumArt> call, Throwable t) {
                completedCalls[0]++;
                // 如果两个回调都已经完成,则调用最终回调
                if (completedCalls[0] == 2) {
                    callback.onDetailsFetched(songUrlResult[0], albumArtUrlResult[0]);
                }
            }
        });
        System.out.println("开始获取歌曲和专辑图URL...");
    }
    // 专用于获取图片URL的回调接口
    public interface OnAlbumArtUrlFetched {
        void onUrlFetched(String url);
    }

    // 专门获取图片URL的方法
    public void fetchAlbumArtUrl(String picId, String source, OnAlbumArtUrlFetched callback) {
        apiService.getAlbumArt(picId, source, 300).enqueue(new Callback<AlbumArt>() {
            @Override
            public void onResponse(Call<AlbumArt> call, Response<AlbumArt> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // 请求成功,通过回调返回真实的URL
                    callback.onUrlFetched(response.body().getUrl());
                } else {
                    // 请求失败,返回null
                    callback.onUrlFetched(null);
                }
            }

            @Override
            public void onFailure(Call<AlbumArt> call, Throwable t) {
                // 网络错误,返回null
                callback.onUrlFetched(null);
            }
        });
    }
    private void tryFetchingSongUrlRecursive(SongInfo songInfo, String source, final int[] bitrates, int index, OnAlbumArtUrlFetched finalCallback) {
        // 如果索引超出了数组范围,说明所有音质都尝试失败了
        if (index >= bitrates.length) {
            finalCallback.onUrlFetched(null); // 返回null表示失败
            return;
        }

        // 获取当前要尝试的音质
        int currentBitrate = bitrates[index];

        apiService.getSongUrl(songInfo.getId(), source, currentBitrate).enqueue(new Callback<SongUrl>() {
            @Override
            public void onResponse(Call<SongUrl> call, Response<SongUrl> response) {
                // 检查当前音质是否获取成功
                if (response.isSuccessful() && response.body() != null && response.body().getUrl() != null) {
                    // 立即通过回调返回URL,停止递归
                    finalCallback.onUrlFetched(response.body().getUrl());
                } else {
                    // 失败,递归调用自身,尝试下一个音质 (index + 1)
                    tryFetchingSongUrlRecursive(songInfo, source, bitrates, index + 1, finalCallback);
                }
            }

            @Override
            public void onFailure(Call<SongUrl> call, Throwable t) {
                // 网络失败,同样递归调用自身,尝试下一个音质
                tryFetchingSongUrlRecursive(songInfo, source, bitrates, index + 1, finalCallback);
            }
        });
    }
}
