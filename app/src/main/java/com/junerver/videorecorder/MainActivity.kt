package com.junerver.videorecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.junerver.videorecorder.pip.PipManager
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
    val REQUEST_VIDEO = 99
    private lateinit var pipManager: PipManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mBtnRecord.setOnClickListener {
            rxRequestPermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO,
                describe = "相机、存储、录音"
            ) {
                startActivityForResult(
                    Intent(
                        this@MainActivity,
                        VideoRecordActivity::class.java
                    ), REQUEST_VIDEO
                )
            }
        }
        mBtnRecord3.setOnClickListener {
            rxRequestPermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO,
                describe = "相机、存储、录音"
            ) {
                startActivityForResult(
                    Intent(
                        this@MainActivity,
                        VideoRecordActivityJava::class.java
                    ), REQUEST_VIDEO
                )
            }
        }
        mBtnRecord2.setOnClickListener {
            if (checkDrawOverlayPermission()) {
                pipManager = PipManager(this)
                pipManager.enterPip()
            }
        }
        checkDrawOverlayPermission()
    }

    fun checkDrawOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        return if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1001)
            false
        } else {
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_VIDEO) {
                var path = data?.getStringExtra("path")
                var imgPath = data?.getStringExtra("imagePath")
                val type = data?.getIntExtra("type", -1)
                if (type == TYPE_VIDEO) {
                    mTvResult.text = "视频地址：\n\r$path \n\r缩略图地址：\n\r$imgPath"
                } else if (type == TYPE_IMAGE) {
                    mTvResult.text = "图片地址：\n\r$imgPath"
                }
            }
        }
    }
}
