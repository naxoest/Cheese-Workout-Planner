package com.gymwp.free.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Manages capturing, downscaling, EXIF-rotating, persisting, and backing up custom exercise photos.
 */
object ExercisePhotoManager {

    private const val PHOTO_DIR = "exercise_photos"
    private const val MAX_DIMENSION_PX = 760
    private const val JPEG_QUALITY = 82

    fun getPhotosDir(context: Context): File {
        val dir = File(context.filesDir, PHOTO_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createTempCameraUri(context: Context): Pair<File, Uri> {
        val tempFile = File(context.cacheDir, "cam_ex_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return tempFile to uri
    }

    /**
     * Reads an image from a Camera or Gallery Uri, corrects EXIF orientation,
     * scales down to <= 760px on the longest edge, and saves it into internal filesDir.
     */
    fun importAndSavePhoto(context: Context, sourceUri: Uri): String? {
        return try {
            // 1. Read EXIF rotation
            val rotationDegrees = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            // 2. Decode bounds first to prevent OOM on 50MP+ camera photos
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            var sampleSize = 1
            while (max(rawW, rawH) / sampleSize > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            // 3. Decode sampled bitmap
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            // 4. Rotate and scale proportionally to MAX_DIMENSION_PX
            val matrix = Matrix()
            if (rotationDegrees != 0f) {
                matrix.postRotate(rotationDegrees)
            }
            val rotated = if (rotationDegrees != 0f) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else {
                decoded
            }
            if (rotated !== decoded) decoded.recycle()

            val longest = max(rotated.width, rotated.height)
            val finalBitmap = if (longest > MAX_DIMENSION_PX) {
                val ratio = MAX_DIMENSION_PX.toFloat() / longest.toFloat()
                val targetW = (rotated.width * ratio).roundToInt().coerceAtLeast(1)
                val targetH = (rotated.height * ratio).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(rotated, targetW, targetH, true).also {
                    if (it !== rotated) rotated.recycle()
                }
            } else {
                rotated
            }

            // 5. Persist compressed JPEG in internal storage
            val destFile = File(getPhotosDir(context), "ex_photo_${System.currentTimeMillis()}.jpg")
            FileOutputStream(destFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.flush()
            }
            finalBitmap.recycle()
            destFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeLocalPath(pathOrAsset: String?): String? {
        if (pathOrAsset.isNullOrBlank()) return null
        val cleaned = pathOrAsset.removePrefix("file://").trim()
        return cleaned.takeIf { it.startsWith("/") && File(it).exists() }
    }

    fun deletePhotoIfManaged(context: Context, pathOrAsset: String?) {
        val raw = pathOrAsset?.removePrefix("file://")?.trim().orEmpty()
        if (raw.isBlank()) return
        try {
            val file = File(raw)
            val managedRoot = getPhotosDir(context).canonicalPath
            if (file.exists() && file.canonicalPath.startsWith(managedRoot)) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun encodePhotoToBase64(localPath: String?): String? {
        val valid = normalizeLocalPath(localPath) ?: return null
        return try {
            val bytes = File(valid).readBytes()
            if (bytes.isEmpty() || bytes.size > 600_000) null
            else Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun restorePhotoFromBase64(context: Context, exerciseId: Int, base64: String): String? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            val dest = File(getPhotosDir(context), "ex_restored_${exerciseId}_${System.currentTimeMillis()}.jpg")
            dest.writeBytes(bytes)
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}

data class DecodedCustomExerciseMeta(
    val equipment: List<String>,
    val photoPath: String?,
    val notes: String
)

object CustomExerciseCodec {
    private const val EQ_PREFIX = "[EQ:"
    private const val IMG_PREFIX = "[IMG:"
    private const val SUFFIX = "]"

    fun encodeDescription(
        equipment: List<String>,
        notes: String,
        photoPath: String? = null
    ): String {
        val cleanEq = equipment.ifEmpty { listOf("Aparato / Máquina") }.joinToString("|")
        val cleanPhoto = photoPath?.removePrefix("file://")?.trim().orEmpty()
        val imgTag = if (cleanPhoto.isNotBlank()) "$IMG_PREFIX$cleanPhoto$SUFFIX" else ""
        return "$EQ_PREFIX$cleanEq$SUFFIX$imgTag\n${notes.trim()}"
    }

    fun decodeMeta(raw: String): DecodedCustomExerciseMeta {
        var rest = raw
        var eqList = listOf("Aparato / Máquina")
        var photoPath: String? = null

        if (rest.startsWith(EQ_PREFIX)) {
            val endEq = rest.indexOf(SUFFIX)
            if (endEq > EQ_PREFIX.length) {
                val parsed = rest.substring(EQ_PREFIX.length, endEq)
                    .split("|")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (parsed.isNotEmpty()) eqList = parsed
                rest = rest.substring(endEq + 1)
            }
        }
        if (rest.startsWith(IMG_PREFIX)) {
            val endImg = rest.indexOf(SUFFIX)
            if (endImg > IMG_PREFIX.length) {
                photoPath = rest.substring(IMG_PREFIX.length, endImg).trim().takeIf { it.isNotBlank() }
                rest = rest.substring(endImg + 1)
            }
        }
        val cleanNotes = rest.trimStart('\n', '\r', ' ')
        return DecodedCustomExerciseMeta(eqList, photoPath, cleanNotes)
    }

    fun toCatalogExercise(entity: UserCustomExerciseEntity): CatalogExercise {
        val meta = decodeMeta(entity.description)
        val validPhoto = ExercisePhotoManager.normalizeLocalPath(meta.photoPath)
        return CatalogExercise(
            id = entity.id,
            name = entity.name,
            description = meta.notes,
            muscles = entity.muscles.padEnd(17, '0').take(17),
            defaultUnitType = entity.unitType,
            equipment = meta.equipment,
            gifAssetPath = if (validPhoto != null) "file://$validPhoto" else ""
        )
    }
}
