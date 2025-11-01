package com.arturo254.opentune

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.arturo254.opentune.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusicWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> PlayerConnection.instance?.togglePlayPause()
            ACTION_PREV -> PlayerConnection.instance?.seekToPrevious()
            ACTION_NEXT -> PlayerConnection.instance?.seekToNext()
            ACTION_SHUFFLE -> PlayerConnection.instance?.toggleShuffle()
            ACTION_LIKE -> PlayerConnection.instance?.toggleLike()
            ACTION_LYRICS -> PlayerConnection.instance?.toggleLyrics()
            ACTION_OPEN_APP -> openPlayerActivity(context)
            ACTION_METADATA_CHANGED, ACTION_UPDATE_PROGRESS -> updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.Arturo254.opentune.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.Arturo254.opentune.ACTION_PREV"
        const val ACTION_NEXT = "com.Arturo254.opentune.ACTION_NEXT"
        const val ACTION_SHUFFLE = "com.Arturo254.opentune.ACTION_SHUFFLE"
        const val ACTION_LIKE = "com.Arturo254.opentune.ACTION_LIKE"
        const val ACTION_LYRICS = "com.Arturo254.opentune.ACTION_LYRICS"
        const val ACTION_METADATA_CHANGED = "com.Arturo254.opentune.ACTION_METADATA_CHANGED"
        const val ACTION_UPDATE_PROGRESS = "com.Arturo254.opentune.ACTION_UPDATE_PROGRESS"
        const val ACTION_OPEN_APP = "com.Arturo254.opentune.ACTION_OPEN_APP"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MusicWidget::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_music)
            val player = PlayerConnection.instance?.player

            // --- Song and artist ---
            val title = player?.mediaMetadata?.title?.toString() ?: context.getString(R.string.song_title)
            val artist = player?.mediaMetadata?.artist?.toString() ?: context.getString(R.string.artist_name)
            views.setTextViewText(R.id.widget_song_title, title)
            views.setTextViewText(R.id.widget_artist, artist)

            // --- Album art with fallback (circle with initials) ---
            val artworkUri = player?.mediaMetadata?.artworkUri?.toString()
            if (!artworkUri.isNullOrEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val req = ImageRequest.Builder(context)
                            .data(artworkUri)
                            .size(200, 200)
                            .build()
                        val drawable = ImageLoader(context).execute(req).drawable
                        drawable?.let { d ->
                            views.setImageViewBitmap(R.id.widget_album_art, d.toBitmap())
                            manager.partiallyUpdateAppWidget(id, views)
                        }
                    } catch (_: Exception) {
                        views.setImageViewBitmap(R.id.widget_album_art, fallbackBitmap(artist, context))
                        manager.partiallyUpdateAppWidget(id, views)
                    }
                }
            } else {
                views.setImageViewBitmap(R.id.widget_album_art, fallbackBitmap(artist, context))
            }

            // --- Progress bar ---
            val duration = player?.duration ?: 0L
            val pos = player?.currentPosition ?: 0L
            val progress = if (duration > 0) (pos * 100 / duration).toInt() else 0
            views.setProgressBar(R.id.widget_progress_bar, 100, progress, false)

            // --- Controls state + feedback visual (puedes mejorar con animaciones si quieres) ---
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (player?.playWhenReady == true) R.drawable.pause else R.drawable.play
            )
            views.setImageViewResource(
                R.id.widget_shuffle,
                if (player?.shuffleModeEnabled == true) R.drawable.shuffle_on else R.drawable.shuffle
            )
            views.setImageViewResource(
                R.id.widget_like,
                if (PlayerConnection.instance?.isCurrentSongLiked() == true)
                    R.drawable.favorite else R.drawable.favorite_border
            )

            // --- Next song (queue) ---
            val nextTitle = PlayerConnection.instance?.getNextSongTitle()
            views.setTextViewText(
                R.id.widget_next_up,
                nextTitle?.let { "Next: $it" } ?: ""
            )

            // --- Set control intents ---
            setPendingIntents(context, views)
            // --- Set general open app intent (al pulsar el widget) ---
            setOpenAppIntent(context, views)

            // --- Update widget ---
            manager.updateAppWidget(id, views)
        }

        private fun setPendingIntents(context: Context, views: RemoteViews) {
            views.setOnClickPendingIntent(R.id.widget_play_pause, getPendingIntent(context, ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_prev, getPendingIntent(context, ACTION_PREV))
            views.setOnClickPendingIntent(R.id.widget_next, getPendingIntent(context, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widget_shuffle, getPendingIntent(context, ACTION_SHUFFLE))
            views.setOnClickPendingIntent(R.id.widget_like, getPendingIntent(context, ACTION_LIKE))
            views.setOnClickPendingIntent(R.id.widget_lyrics, getPendingIntent(context, ACTION_LYRICS))
        }

        private fun setOpenAppIntent(context: Context, views: RemoteViews) {
            views.setOnClickPendingIntent(R.id.widget_root, getPendingIntent(context, ACTION_OPEN_APP))
        }

        private fun getPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, MusicWidget::class.java).apply { this.action = action }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getBroadcast(context, action.hashCode(), intent, flags)
        }

        private fun openPlayerActivity(context: Context) {
            val i = Intent(context, PlayerActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            context.startActivity(i)
        }

        // Fallback para carátula: círculo con iniciales del artista, color dinámico
        private fun fallbackBitmap(artist: String?, context: Context): Bitmap {
            val size = 200
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint()
            paint.isAntiAlias = true
            paint.color = dynamicColorFromString(artist ?: "", context)
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.color = Color.WHITE
            paint.textSize = 64f
            paint.textAlign = Paint.Align.CENTER
            val initials = artist?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            canvas.drawText(initials, size / 2f, size / 1.6f, paint)
            return bmp
        }

        private fun dynamicColorFromString(source: String, context: Context): Int {
            // Simple hash to color (puedes mejorar usando Material You dynamic color)
            return Color.parseColor("#" + Integer.toHexString(source.hashCode()).takeLast(6).padStart(6, 'F'))
        }
    }
}
