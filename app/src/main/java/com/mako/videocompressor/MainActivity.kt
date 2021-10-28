package com.mako.videocompressor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mako.videocompressor.utils.AndroidUtilities
import com.mako.videocompressor.utils.VideoEditedInfo
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File


class MainActivity : AppCompatActivity() {


    companion object {
        const val REQUEST_CODE_WRITE_STORAGE = 2
        const val REQUEST_CODE_COMPRESS_VIDEO: Int = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<android.widget.Button>(R.id.btnChooseVideo).setOnClickListener {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_WRITE_STORAGE
            )
        } else {
            chooseVideo()
        }
    }

    private fun chooseVideo() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "video/*"
        startActivityForResult(intent, REQUEST_CODE_COMPRESS_VIDEO)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_WRITE_STORAGE -> {

                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    chooseVideo()
                } else {
                    Toast.makeText(
                        this, "You need to enable the permission for External Storage Write" +
                                " to test out this library.", Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_COMPRESS_VIDEO) {
            var uri = data.data

            Thread {

                val path = AndroidUtilities.getPath(uri)
                val createCompressionSettings = MediaController.createCompressionSettings(path)
                val isSuccess = MediaController.convertVideo(
                    createCompressionSettings
                ) { cacheFile, availableSize, progress, info ->

                    val message = Message.obtain()

                    val bundle = Bundle()

                    bundle.putLong("availableSize", availableSize)
                    bundle.putFloat("progress", progress)
                    bundle.putString("cacheFile", cacheFile)

                    message.arg1 = 1

                    message.data = bundle

                    message.obj = info

                    videoCompressHandler.sendMessage(message)

                }

            }.start()

        }
    }

    private val videoCompressHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.arg1 == 1) {

                val info = msg.obj as? VideoEditedInfo

                val data = msg.data

                val progress = data.getFloat("progress")
                val availableSize = data.getLong("availableSize")
                val cacheFile = data.getString("cacheFile")

                info?.apply {
                    tv.text = """
originalPath:${info.originalPath}
originalHeight:${info.originalHeight}
originalWidth:${info.originalWidth}
originalSize:${AndroidUtilities.formatFileSize(File(originalPath).length())}

-------------------------------------------------------------------

compressPath:${cacheFile}
resultHeight:${info.resultHeight}
resultWidth:${info.resultWidth}
availableSize:${AndroidUtilities.formatFileSize(availableSize)}
progress:${progress * 100}
                            
                        """.trimMargin()
                }
            }
        }
    }


}