package app.intack

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.intack.ui.LocalTextScale
import app.intack.ui.ScaledText
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.intack.data.Diagnostics
import app.intack.model.Invite
import app.intack.model.ShareLink
import app.intack.sync.Notifications
import app.intack.ui.DiagnosticsOverlay
import app.intack.ui.QrImage
import app.intack.ui.IntackRoot
import app.intack.ui.NotificationFocus
import kotlinx.coroutines.launch
import app.intack.ui.IntackTheme
import app.intack.ui.themeById

class MainActivity : ComponentActivity() {
    private val app get() = application as IntackApp
    private val pendingInvite = mutableStateOf<Invite?>(null)
    private val pendingOpen = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only once: turning the phone round recreates the activity with the same intent.
        if (savedInstanceState == null) handleIntent(intent)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val themeId by app.appTheme.collectAsStateWithLifecycle()
            // The open list may have its own theme; the status bar icons follow whatever is on screen.
            var screenDark by remember { mutableStateOf(themeById(themeId).dark) }
            LaunchedEffect(screenDark) {
                val bars = if (screenDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(bars, bars)
            }
            val textScale by app.textScale.collectAsStateWithLifecycle()
            // Larger text everywhere, on top of the phone's own font size setting (dialogs apply it again: Dialogs.kt).
            CompositionLocalProvider(LocalTextScale provides textScale) { ScaledText {
                IntackTheme(themeById(themeId)) {
                    IntackRoot(
                        app,
                        pendingInvite.value,
                        onInviteHandled = { pendingInvite.value = null },
                        openRequest = pendingOpen.value,
                        onOpenHandled = { pendingOpen.value = null },
                        onScreenDark = { screenDark = it },
                    )
                }
            } }
            val diagnostics by Diagnostics.enabled.collectAsStateWithLifecycle()
            if (diagnostics) DiagnosticsOverlay()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        app.visibleActivities++
        Diagnostics.setVisible(true)
        app.sync.setOnScreen(true)
        app.sync.acquire()
    }

    override fun onStop() {
        app.sync.release()
        app.visibleActivities--
        if (app.visibleActivities == 0) {
            Diagnostics.setVisible(false)
            app.sync.setOnScreen(false)
        }
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(Notifications.EXTRA_LIST_ID)?.let { listId ->
            intent.getStringArrayExtra(Notifications.EXTRA_ITEM_IDS)?.let { NotificationFocus.request(listId, it.toList()) }
            pendingOpen.value = listId
        }
        intent?.dataString?.let(ShareLink::parse)?.let { pendingInvite.value = it }
        // A screenshot of someone's QR code shared to Intack from the gallery.
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
            lifecycleScope.launch {
                QrImage.decode(this@MainActivity, uri)?.let(ShareLink::parse)?.let { pendingInvite.value = it }
                    ?: Toast.makeText(this@MainActivity, getString(R.string.no_code_in_image, getString(R.string.brand_name)), Toast.LENGTH_LONG).show()
            }
        }
    }
}
