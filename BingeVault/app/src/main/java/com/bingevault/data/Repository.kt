package com.bingevault.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class Repository(private val context: Context) {

    private val dao = AppDatabase.get(context).mediaDao()

    // Posters stored as compressed JPEGs in private app storage.
    // At ~15–25 KB each, 100 k titles ≈ 1.5–2.5 GB worst-case;
    // real-world usage is far lower since most titles won't have custom art.
    private val imgDir = File(context.filesDir, "posters").also { it.mkdirs() }

    // ── Read ─────────────────────────────────────────────────────────────────

    fun getAll(): Flow<List<MediaWithSeasons>> = dao.getAllWithSeasons()

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Insert or update a [MediaItem] together with its seasons atomically.
     * All existing seasons for the item are replaced (deleted + re-inserted)
     * so the caller never has to diff the list.
     */
    suspend fun save(item: MediaItem, seasons: List<Season>): Long {
        val id = if (item.id == 0L) dao.insertMedia(item)
                 else { dao.updateMedia(item); item.id }
        dao.deleteSeasonsForMedia(id)
        dao.insertSeasons(seasons.mapIndexed { i, s -> s.copy(mediaId = id, sortOrder = i) })
        return id
    }

    suspend fun updateSeason(season: Season) = dao.updateSeason(season)

    suspend fun delete(items: List<MediaItem>) {
        items.forEach { it.posterPath?.let { p -> File(imgDir, p).delete() } }
        dao.deleteMediaList(items)
    }

    // ── Images ───────────────────────────────────────────────────────────────

    /**
     * Decodes the picked URI, scales to ≤300×450, and saves as JPEG q=65.
     * Returns the filename (not full path) stored in [MediaItem.posterPath].
     *
     * Optimisation summary:
     *  - Max resolution 300×450 — enough for a grid poster, ~15–30 KB on disk.
     *  - JPEG quality 65 — visually good, compresses well.
     *  - inSampleSize used when source is large to avoid OOM during decode.
     *  - Files are stored in app-private storage (no external permission needed
     *    for writing, and the DB + images travel together as one "vault").
     */
    suspend fun saveImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            opts.inSampleSize = calcSample(opts.outWidth, opts.outHeight, 300, 450)
            opts.inJustDecodeBounds = false
            val bmp = context.contentResolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@withContext null
            val scaled = scale(bmp, 300, 450)
            val name = "${UUID.randomUUID()}.jpg"
            File(imgDir, name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 65, it) }
            if (scaled != bmp) bmp.recycle()
            name
        } catch (_: Exception) { null }
    }

    fun imageFile(name: String): File = File(imgDir, name)

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun calcSample(w: Int, h: Int, maxW: Int, maxH: Int): Int {
        var s = 1
        while (w / (s * 2) >= maxW && h / (s * 2) >= maxH) s *= 2
        return s
    }

    private fun scale(bmp: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val r = minOf(maxW.toFloat() / bmp.width, maxH.toFloat() / bmp.height, 1f)
        return if (r < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * r).toInt(), (bmp.height * r).toInt(), true)
               else bmp
    }
}
