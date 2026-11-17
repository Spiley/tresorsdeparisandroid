package com.tresorsdeparis

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.tresorsdeparis.data.FavoritesStore
import com.tresorsdeparis.data.Lang
import com.tresorsdeparis.data.NewPostNotifier
import com.tresorsdeparis.data.TripStore
import com.tresorsdeparis.data.WebTranslator
import com.tresorsdeparis.data.WordPressApi
import com.tresorsdeparis.data.t
import com.tresorsdeparis.ui.LocalFavorites
import com.tresorsdeparis.ui.LocalTrips
import com.tresorsdeparis.ui.TresorsApp
import com.tresorsdeparis.ui.theme.TresorsDeParisTheme

class MainActivity : ComponentActivity() {
    /** An article to open, from a tapped notification. */
    private var openPostId by mutableStateOf<Int?>(null)

    /** Set when Google Play has a newer version; shows the update popup. */
    private var updateOffered by mutableStateOf(false)
    private var updateInfo: AppUpdateInfo? = null
    private val updateManager by lazy { AppUpdateManagerFactory.create(this) }
    private val updateFlow = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        WordPressApi.init(this)
        Lang.init(this)
        WebTranslator.init(this)
        val favorites = FavoritesStore(this)
        val trips = TripStore(this)
        // `--ei startTab N` opens another tab (used for emulator screenshots)
        val startTab = intent.getIntExtra("startTab", 0)
        openPostId = intent.postId()

        NewPostNotifier.createChannel(this)
        NewPostNotifier.schedule(this)
        if (isDebuggable()) intent.getLongExtra("checkPostsSince", 0).takeIf { it > 0 }?.let { NewPostNotifier.checkNow(this, it) }
        askForNotificationsOnce()
        checkForUpdate()

        setContent {
            TresorsDeParisTheme {
                CompositionLocalProvider(LocalFavorites provides favorites, LocalTrips provides trips) {
                    TresorsApp(startTab, openPostId, onPostOpened = { openPostId = null })
                    if (updateOffered) {
                        UpdateDialog(
                            onUpdate = { updateOffered = false; startUpdate() },
                            onLater = { updateOffered = false },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.postId()?.let { openPostId = it }
    }

    override fun onResume() {
        super.onResume()
        // An update that was started but not finished (e.g. the app was closed) continues
        updateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                updateManager.startUpdateFlowForResult(info, updateFlow, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
            }
        }
    }

    private fun isDebuggable() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun Intent.postId(): Int? =
        getIntExtra(NewPostNotifier.ExtraPostId, -1).takeIf { it > 0 }

    /** Android 13 and newer ask before showing notifications; asked once, at the first start. */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences("notifications", MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Asks Google Play whether there is a newer version, and offers it at most once a day.
     * `--ez showUpdate true` shows the popup on a test build, which Play does not know.
     */
    private fun checkForUpdate() {
        if (isDebuggable() && intent.getBooleanExtra("showUpdate", false)) {
            updateOffered = true
            return
        }
        updateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@addOnSuccessListener
            val prefs = getSharedPreferences("updates", MODE_PRIVATE)
            val key = "offered-${info.availableVersionCode()}"
            if (System.currentTimeMillis() - prefs.getLong(key, 0) < 24 * 60 * 60 * 1000L) return@addOnSuccessListener
            prefs.edit().putLong(key, System.currentTimeMillis()).apply()
            updateInfo = info
            updateOffered = true
        }
    }

    /** Google Play's own update screen, or the store page when that is not possible. */
    private fun startUpdate() {
        val info = updateInfo
        if (info != null && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            updateManager.startUpdateFlowForResult(info, updateFlow, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }
}

@androidx.compose.runtime.Composable
private fun UpdateDialog(onUpdate: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        icon = {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.SystemUpdate,
                contentDescription = null,
            )
        },
        title = { Text(t("Update available")) },
        text = { Text(t("A new version of Trésors de Paris is available. Update now to get the latest improvements.")) },
        confirmButton = { TextButton(onClick = onUpdate) { Text(t("Update")) } },
        dismissButton = { TextButton(onClick = onLater) { Text(t("Later")) } },
    )
}
