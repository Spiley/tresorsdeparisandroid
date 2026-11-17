package com.tresorsdeparis.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.tresorsdeparis.MainActivity
import com.tresorsdeparis.R
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Notifies about new articles. The app has no push server, so it looks at the website's
 * newest articles every few hours in the background and shows a notification for any that
 * appeared since the last look.
 */
object NewPostNotifier {
    private const val Channel = "new_articles"
    private const val WorkName = "new-articles"
    const val ExtraPostId = "postId"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("notifications", Context.MODE_PRIVATE)

    /** Starts the background check; keeps an already planned one. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NewPostWorker>(3, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WorkName, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Remembers the newest article the reader has seen in the app, so it is not announced. */
    fun markSeen(context: Context, posts: List<Post>) {
        val newest = posts.maxOfOrNull { it.date } ?: return
        val prefs = prefs(context)
        if (newest > prefs.getLong("newestSeen", 0)) prefs.edit().putLong("newestSeen", newest).apply()
    }

    /**
     * Test builds: checks right away as if the articles after [since] were not seen yet.
     * Started with `--el checkPostsSince <millis>`.
     */
    fun checkNow(context: Context, since: Long) {
        val request = OneTimeWorkRequestBuilder<NewPostWorker>()
            .setInputData(workDataOf("since" to since))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /** Shows a notification for the articles newer than the last one seen (or than [since]). */
    suspend fun announce(context: Context, posts: List<Post>, since: Long? = null) {
        val prefs = prefs(context)
        val lastSeen = since ?: prefs.getLong("newestSeen", 0)
        markSeen(context, posts)
        // The very first check only notes where the website is
        if (lastSeen == 0L) return
        val fresh = posts.filter { it.date > lastSeen }.sortedByDescending { it.date }
        if (fresh.isNotEmpty()) show(context, fresh)
    }

    @SuppressLint("MissingPermission") // Checked by areNotificationsEnabled
    private suspend fun show(context: Context, fresh: List<Post>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel(context)

        val post = fresh.first()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Several new articles open Home, where they are at the top
            .apply { if (fresh.size == 1) putExtra(ExtraPostId, post.id) }
        val pendingIntent = PendingIntent.getActivity(
            context, post.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (fresh.size == 1) t("New article") else t("%d new articles", fresh.size)
        val text = fresh.joinToString("\n") { it.title }
        val builder = NotificationCompat.Builder(context, Channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB8892B.toInt())
            .setContentTitle(title)
            .setContentText(post.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        image(context, post.imageUrl)?.let { builder.setLargeIcon(it) }
        manager.notify(1, builder.build())
    }

    private suspend fun image(context: Context, url: String?): Bitmap? {
        url ?: return null
        return try {
            val request = ImageRequest.Builder(context).data(url).size(256).allowHardware(false).build()
            (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(Channel, t("New articles"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = t("A notification when a new article is posted")
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** The background check for new articles, run every few hours by WorkManager. */
class NewPostWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WordPressApi.init(applicationContext)
        Lang.init(applicationContext)
        return try {
            val since = inputData.getLong("since", 0).takeIf { it > 0 }
            NewPostNotifier.announce(applicationContext, WordPressApi.posts(1, PostSource.Latest, fresh = true).posts, since)
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.retry()
        }
    }
}
