package com.grabbit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.grabbit.core.Languages
import com.grabbit.core.Links
import com.grabbit.data.settings.SettingsRepository
import com.grabbit.ui.AppRoot
import com.grabbit.ui.AppViewModel
import com.grabbit.ui.theme.GrabbitTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val incomingLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Apply the stored language (or system default) before the first frame
        // so the UI opens in the right locale.
        // Applying an empty locale list on every cold start makes AppCompat
        // recreate the Activity. Only a stored explicit language needs applying
        // here; switching to "system default" is handled in settings.
        lifecycleScope.launch {
            settingsRepository.current().language?.let(Languages::apply)
        }
        handleIntent(intent)

        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val context = LocalContext.current

            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

            GrabbitTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                AppRoot(
                    incomingLink = incomingLink,
                    onConsumeLink = { incomingLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        val link = Links.extract(text) ?: return
        incomingLink.value = link
    }
}
