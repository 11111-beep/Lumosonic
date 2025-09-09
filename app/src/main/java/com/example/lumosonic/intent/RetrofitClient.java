package com.example.lumosonic.intent;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

// 用于创建一个经过配置的、可供整个 App 重复使用的、
// 用于和服务器 https://music-api.gdstudio.xyz/ 进行通信的客户端
public class RetrofitClient {

    public static final String BASE_URL = "https://music-api.gdstudio.xyz/";
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return retrofit;
    }
    // 获取MusicApiService的实例
    public static MusicApiService getService() {
        return getClient().create(MusicApiService.class);
    }
}
