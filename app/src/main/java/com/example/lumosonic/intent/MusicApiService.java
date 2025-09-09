package com.example.lumosonic.intent;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

// 为Retrofit创建API接口
public interface MusicApiService {

    // 搜索歌曲
    @GET("api.php?types=search")
    Call<List<SongInfo>> searchSongs(
            @Query("name") String keywords,
            @Query("source") String  source
    );

    // 获取歌曲详情
    @GET("api.php?types=url")
    Call<SongUrl> getSongUrl(
            @Query("id") long trackId,
            @Query("source") String source,
            @Query("br") int bitRate
    );

    // 获取专辑图片
    @GET("api.php?types=pic")
    Call<AlbumArt> getAlbumArt(
            @Query("id") String picId,
            @Query("source") String source,
            @Query("size") int size
    );
}
