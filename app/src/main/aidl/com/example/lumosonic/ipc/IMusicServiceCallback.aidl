// IMusicServiceCallback.aidl
package com.example.lumosonic.ipc;

/*
根本原因在于您的 UI 更新机制。您目前采用的是“轮询”（Polling）的方式：
在 MusicActivity 中，您使用一个Handler每隔1秒去向MusicService查询一次当前的状态然后更新UI。
当一首歌播放失败时，会发生以下一系列事件：
瞬间发生：MusicService 的 onError() 方法被调用。
瞬间发生：onError() 立即调用 next()，next() 再调用 play(newIndex)。
此时，服务内部的 currentSongIndex 已经变了，getSongTitle() 如果被调用，会返回新歌的标题。
等待发生：然而，您的 MusicActivity 对这一切毫不知情。
它正在等待 handler.postDelayed(..., 1000) 的下一次触发。
延迟出现：在这最多1秒的延迟里，界面上显示的仍然是上一首（播放失败的）歌曲的标题。
NeumorphTextView 根据这个旧的、较长的标题计算并绘制了它的背景和阴影。
最终更新：1秒后，updateUIRunnable 终于执行，它获取到新歌的标题，然后调用 title.setText()。
这时，文字内容更新了，但 NeumorphTextView 可能因为某些内部绘制机制，没能完美地触发阴影的重绘，
或者即使重绘了，那1秒的视觉延迟也已经被你捕捉到了。
结论：问题不在于 NeumorphTextView，而在于你的应用架构。
UI 和 Service 的状态是“脱节”的，UI 的更新不是由事件驱动的，而是靠运气（轮询周期正好赶上）。
*/

/*
要彻底解决这个问题，你需要让 Service 在状态发生变化时，主动通知 Activity，
而不是让 Activity 傻傻地每秒都来问一次。这就要用到我们之前讨论过的AIDL 回调（Callback）机制。
*/

// 最好使用 oneway,表示异步调用,不会阻塞Service,通过回调模式进行通信,当服务端状态发生变化时,会主动调用客户端的方法
oneway interface IMusicServiceCallback {
    // 当歌曲切换时调用
    void onSongChanged(String title, String artist, String albumArtUri);
    // 当播放状态改变时调用 (播放/暂停)
    void onPlaybackStateChanged(boolean isPlaying);
    // 当播放列表或模式改变时可以添加更多回调...
}