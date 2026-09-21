package `in`.cashlessconsumer.zovoice.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Hands the downloaded APK to the system package installer. */
object ApkInstaller {
    const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".updates"

    fun updateDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "updates")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun apkFile(context: Context): File = File(updateDir(context), "zo-voice-update.apk")

    /** Fires ACTION_VIEW with a FileProvider URI — the system installer takes it from here. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
