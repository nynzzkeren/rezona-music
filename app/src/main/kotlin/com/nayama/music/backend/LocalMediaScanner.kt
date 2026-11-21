package com.nayama.music.backend

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.nayama.music.model.RezonaTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Scans local audio files via Android MediaStore when READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE is granted.
 */
object LocalMediaScanner {

    suspend fun scanDeviceAudio(context: Context, filterQuery: String = ""): List<RezonaTrack> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<RezonaTrack>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            val albumArtBase = Uri.parse("content://media/external/audio/albumart")

            runCatching {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                    val q = filterQuery.trim().lowercase()
                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol).orEmpty().ifBlank { "Untitled Audio" }
                        val artist = cursor.getString(artistCol).orEmpty().let {
                            if (it == "<unknown>" || it.isBlank()) "Local Artist" else it
                        }
                        val album = cursor.getString(albumCol).orEmpty().ifBlank { "On device" }
                        if (q.isNotEmpty() && !title.lowercase().contains(q) && !artist.lowercase().contains(q) && !album.lowercase().contains(q)) {
                            continue
                        }
                        val durMs = cursor.getLong(durCol).coerceAtLeast(0L)
                        val durStr = String.format(Locale.ENGLISH, "%d:%02d", durMs / 60000L, (durMs % 60000L) / 1000L)
                        val albumId = cursor.getLong(albumIdCol)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                        val artUri = ContentUris.withAppendedId(albumArtBase, albumId).toString()

                        list.add(
                            RezonaTrack(
                                id = "local_$mediaId",
                                index = list.size,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = durStr,
                                source = "On device",
                                coverArtUrl = artUri,
                                streamUrl = contentUri.toString()
                            )
                        )
                    }
                }
            }
            list
        }
}
