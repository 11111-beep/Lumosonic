// IMusicService.aidl
package com.example.lumosonic.ipc;

// Declare any non-default types here with import statements
import com.example.lumosonic.ipc.Song;

import com.example.lumosonic.ipc.IMusicServiceCallback;

/*
进程一：主应用 / UI 进程
名称：com.example.lumosonic (您的应用包名)
运行着：MusicActivity 以及您应用中所有其他没有特殊指定的组件。
职责：负责显示用户界面、处理用户点击等交互。

进程二：服务进程 / 远程进程
名称：com.example.lumosonic:remote (您的应用包名 + 您指定的名字)
运行着：只有 MusicService 组件。
职责：专门在后台播放音乐，不受 UI 进程的影响。

把 Service 放在独立进程中的巨大优势：
稳定性：如果您的 MusicActivity 因为某个UI操作或内存问题崩溃了（这在复杂的UI中很常见），
UI进程会死掉，但 Service 进程完全不受影响，音乐会继续播放！ 这对于音乐或导航类应用是至关重要的用户体验。
内存管理：Android 系统可以更独立地管理这两个进程的内存。
当您的应用退到后台时，系统可能会为了回收内存而杀死优先级较低的 UI 进程，
但只要您的 Service 是前台服务（有通知栏），系统就会尽最大努力保留您的服务进程，确保音乐不断。
*/

// 客户端需要调用服务端去执行的命令或者需要从服务端获取的状态信息
interface IMusicService {
   void play(int index);
   void pause();
   void resume();
   void next();
   void previous();
   boolean isPlaying();


   String getSongTitle();
   String getSongArtist();
   // 获取当前播放的歌曲的总时长
   int getDuration();
   // 获取当前播放歌曲的当前播放位置
   int getCurrentPosition();
   // 跳转到指定位置
   void seekTo(int position);
   // 获取当前播放的歌曲的专辑封面URI
   String getCurrentAlbumArtUri();
   // 获取当前播放模式
   int getPlayMode();
   // 设置播放模式
   void setPlayMode(int mode);
   // 注册回调
   void registerCallback(IMusicServiceCallback cb);
   // 取消注册回调
   void unregisterCallback(IMusicServiceCallback cb);
   // 获取当前播放列表
   List<Song> getPlaylist();
   // 获取当前播放列表的索引
   int getCurrentIndex();
   // 下载当前播放歌曲
   void downloadCurrentSong();
   // 临时添加歌曲并播放
   void addAndPlaySong(in Song song);
   // 永久添加到播放列表
   void addSongToPlaylist(in Song song);
   // 删除列表中的歌曲
   void removeSong(int index);
   // 获取本地歌曲
   List<Song> getLocalSongs();
   // 判断是否已收藏
   boolean isFavorite(long songId);
   // 切换收藏状态
   void toggleFavorite(in Song song);
   // 获取收藏歌曲
   List<Song> getFavoriteSongs();
   // 设置播放列表并从指定位置开始播放
   void setPlaylistAndPlay(in List<Song> playlist, int startIndex);
}