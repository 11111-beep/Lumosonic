package com.example.lumosonic

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.lumosonic.ipc.MusicService
import com.permissionx.guolindev.PermissionX

class Permissionx {
    companion object {
        @JvmStatic
        fun requestPermissions(activity: FragmentActivity) {
            val permissions = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
            )

            // 根据 SDK 版本决定请求何种权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                permissions.add(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            } else { // Android13以下
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            PermissionX.init(activity)
                .permissions(permissions)
                .onExplainRequestReason { scope, deniedList ->
                    scope.showRequestReasonDialog(
                        deniedList,
                        "为了应用能够正常工作，请授予以下权限",
                        "确定",
                        "取消"
                    )
                }
                .onForwardToSettings { scope, deniedList ->
                    scope.showForwardToSettingsDialog(
                        deniedList,
                        "您需要在设置中手动授予以下权限",
                        "前往设置",
                        "取消"
                    )
                }
                .request { allGranted, grantedList, deniedList ->
                    if (allGranted) {
                        /*PermissionX 的 allGranted 回调被触发，它会发送一个内容为 ACTION_SCAN_MUSIC 的广播。
                        在后台待命的 MusicService 接收到这个广播。
                        MusicService 的 onReceive 方法被调用，现在才开始在新线程中执行 loadMusic()。
                        因为此时权限已经被授予，扫描会成功，cursor.getCount() 会返回正确的歌曲数量。*/
                        /*val intent = Intent(MusicService.ACTION_SCAN_MUSIC)
                        activity.sendBroadcast(intent)*/
                        val intent = Intent(activity, MusicService::class.java)
                        intent.action = MusicService.ACTION_SCAN_MUSIC // 把命令放在 action 里
                        activity.startService(intent)
                    } else {
                        Toast.makeText(activity, "以下权限被拒绝：$deniedList", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}