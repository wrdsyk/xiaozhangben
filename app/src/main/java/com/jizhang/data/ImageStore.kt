package com.jizhang.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Record photos, kept in the app's private files (`files/images/<name>.jpg`), never in the
 * gallery. Imports are scaled to at most [MAX_SIDE] px and re-encoded as JPEG, so a photo is a
 * few hundred KB and backups stay small enough to embed them.
 */
class ImageStore(private val context: Context) {
    val dir: File = File(context.filesDir, "images").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    /** A fresh `content://` target for the camera ([androidx.activity.result.contract.ActivityResultContracts.TakePicture]). */
    fun newCaptureUri(): Uri {
        val capture = File(context.cacheDir, "capture").apply { mkdirs() }
        capture.listFiles()?.forEach { it.delete() }
        val f = File(capture, "${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    /** Copies a picked or captured photo in; returns its file name, or null if it can't be read. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaled(uri) ?: return@runCatching null
            val name = "${UUID.randomUUID()}.jpg"
            file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            bitmap.recycle()
            name
        }.getOrNull()
    }

    /** Writes a photo restored from a backup. */
    suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (isSafeName(name)) file(name).writeBytes(bytes)
    }

    suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        file(name).takeIf { isSafeName(name) && it.isFile }?.readBytes()
    }

    /** Deletes photos no record refers to (e.g. after a delete whose undo has expired). */
    suspend fun prune(referenced: Set<String>) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - PRUNE_AFTER_MS
        dir.listFiles()?.forEach { f ->
            if (f.name !in referenced && f.lastModified() < cutoff) f.delete()
        }
    }

    private fun decodeScaled(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF rotation itself.
            val source = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = MAX_SIDE.toFloat() / maxOf(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        // API 26–27 only (ImageDecoder handles newer ones).
        val degrees = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
        val scale = (MAX_SIDE.toFloat() / maxOf(raw.width, raw.height)).coerceAtMost(1f)
        if (degrees == 0 && scale == 1f) return raw
        val m = Matrix().apply { postScale(scale, scale); postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
    }

    companion object {
        const val MAX_SIDE = 1600
        const val QUALITY = 82

        /** At most this many photos per record. */
        const val MAX_PER_RECORD = 9

        private const val PRUNE_AFTER_MS = 24L * 60 * 60 * 1000

        fun split(stored: String): List<String> = stored.split('\n').filter { it.isNotBlank() }

        fun join(names: List<String>): String = names.joinToString("\n")

        /** Backup file names come from outside; keep them inside [dir]. */
        fun isSafeName(name: String): Boolean = name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '-' || it == '.' || it == '_' } && !name.startsWith(".")
    }
}
