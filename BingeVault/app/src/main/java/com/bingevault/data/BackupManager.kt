package com.bingevault.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Vault backup format: a .bvault zip containing:
 *   data.json  – all MediaItems + their Seasons as JSON
 *   posters/   – all referenced poster JPEGs
 */
object BackupManager {

    // ── Export / Clone ────────────────────────────────────────────────────────

    suspend fun export(
        context: Context,
        items:   List<MediaWithSeasons>,
        destUri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val imgDir = File(context.filesDir, "posters")
            context.contentResolver.openOutputStream(destUri)!!.buffered().use { out ->
                ZipOutputStream(out).use { zip ->

                    // data.json
                    val json = serialise(items).toByteArray(Charsets.UTF_8)
                    zip.putNextEntry(ZipEntry("data.json"))
                    zip.write(json)
                    zip.closeEntry()

                    // posters
                    items.mapNotNull { it.media.posterPath }.distinct().forEach { name ->
                        val f = File(imgDir, name)
                        if (f.exists()) {
                            zip.putNextEntry(ZipEntry("posters/$name"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
        }
    }

    // ── Merge (import + add non-duplicates by title+type) ─────────────────────

    suspend fun merge(
        context:  Context,
        srcUri:   Uri,
        existing: List<MediaWithSeasons>,
        dao:      MediaDao
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val imgDir = File(context.filesDir, "posters").also { it.mkdirs() }
            val posterBytes = mutableMapOf<String, ByteArray>()
            var jsonText: String? = null

            // Extract zip in one pass
            context.contentResolver.openInputStream(srcUri)!!.buffered().use { inp ->
                ZipInputStream(inp).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "data.json" -> jsonText = zip.readBytes().toString(Charsets.UTF_8)
                            entry.name.startsWith("posters/") -> {
                                val name = entry.name.removePrefix("posters/")
                                posterBytes[name] = zip.readBytes()
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            val imported = deserialise(jsonText ?: return@runCatching 0)
            val existingKeys = existing.map { it.media.title.lowercase() + "|" + it.media.type.name }.toSet()
            var added = 0

            imported.forEach { (media, seasons) ->
                val key = media.title.lowercase() + "|" + media.type.name
                if (key !in existingKeys) {
                    // Remap poster filename to avoid collisions
                    val newPath = media.posterPath?.let { oldName ->
                        val bytes = posterBytes[oldName] ?: return@let null
                        val newName = "${UUID.randomUUID()}.jpg"
                        File(imgDir, newName).writeBytes(bytes)
                        newName
                    }
                    val newId = dao.insertMedia(media.copy(id = 0, posterPath = newPath))
                    dao.insertSeasons(seasons.map { it.copy(id = 0, mediaId = newId) })
                    added++
                }
            }
            added
        }
    }

    // ── JSON serialisation ────────────────────────────────────────────────────

    private fun serialise(items: List<MediaWithSeasons>): String {
        val arr = JSONArray()
        items.forEach { mws ->
            val m = mws.media
            val obj = JSONObject().apply {
                put("id",          m.id)
                put("title",       m.title)
                put("type",        m.type.name)
                put("tags",        m.tags)
                put("posterPath",  m.posterPath ?: JSONObject.NULL)
                put("createdAt",   m.createdAt)
                val sArr = JSONArray()
                mws.seasons.forEach { s ->
                    sArr.put(JSONObject().apply {
                        put("id",              s.id)
                        put("name",            s.name)
                        put("totalEpisodes",   s.totalEpisodes ?: JSONObject.NULL)
                        put("watchedEpisodes", s.watchedEpisodes)
                        put("isOva",           s.isOva)
                        put("isFinished",      s.isFinished)
                        put("sortOrder",       s.sortOrder)
                    })
                }
                put("seasons", sArr)
            }
            arr.put(obj)
        }
        return arr.toString(2)
    }

    private fun deserialise(json: String): List<Pair<MediaItem, List<Season>>> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val media = MediaItem(
                id         = obj.getLong("id"),
                title      = obj.getString("title"),
                type       = ContentType.valueOf(obj.getString("type")),
                tags       = obj.optString("tags", ""),
                posterPath = if (obj.isNull("posterPath")) null else obj.getString("posterPath"),
                createdAt  = obj.optLong("createdAt", System.currentTimeMillis())
            )
            val sArr = obj.getJSONArray("seasons")
            val seasons = (0 until sArr.length()).map { j ->
                val s = sArr.getJSONObject(j)
                Season(
                    id              = s.getLong("id"),
                    name            = s.getString("name"),
                    totalEpisodes   = if (s.isNull("totalEpisodes")) null else s.getInt("totalEpisodes"),
                    watchedEpisodes = s.getInt("watchedEpisodes"),
                    isOva           = s.getBoolean("isOva"),
                    isFinished      = s.getBoolean("isFinished"),
                    sortOrder       = s.getInt("sortOrder")
                )
            }
            media to seasons
        }
    }
}
