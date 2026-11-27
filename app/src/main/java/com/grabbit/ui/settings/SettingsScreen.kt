package com.grabbit.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grabbit.R
import com.grabbit.ui.components.SectionHeader
import com.grabbit.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenConvert: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val version by viewModel.ytDlpVersion.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current

    var showFilenameDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }

    val cookiePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let(viewModel::importCookies) }

    val folderPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri -> uri?.let(viewModel::setCustomFolder) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            scrollBehavior = scrollBehavior,
        )

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(bottom = Dimens.xl),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item { AppearanceSection(settings, viewModel) }
            item { LanguageSection(settings, viewModel) }

            item { SectionHeader(stringResource(R.string.settings_section_downloads)) }
            item {
                DownloadsSection(
                    settings = settings,
                    viewModel = viewModel,
                    onFilename = { showFilenameDialog = true },
                    onPickFolder = { folderPicker.launch(null) },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_extras)) }
            item { ExtrasSection(settings, viewModel) }

            item { SectionHeader(stringResource(R.string.settings_section_network)) }
            item {
                NetworkSection(
                    settings = settings,
                    viewModel = viewModel,
                    onProxy = { showProxyDialog = true },
                    cookiePicker = cookiePicker,
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_updates)) }
            item { UpdatesSection(version, updateState, settings, viewModel) }

            item { SectionHeader(stringResource(R.string.settings_section_tools)) }
            item { ToolsSection(onOpenConvert) }

            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item { AboutSection() }
        }
    }

    if (showFilenameDialog) {
        TextInputDialog(
            title = stringResource(R.string.settings_filename),
            initial = settings.filenameTemplate,
            onDismiss = { showFilenameDialog = false },
            onConfirm = {
                viewModel.setFilenameTemplate(it)
                showFilenameDialog = false
            },
        )
    }

    if (showProxyDialog) {
        TextInputDialog(
            title = stringResource(R.string.settings_proxy),
            initial = settings.proxy,
            placeholder = stringResource(R.string.settings_proxy_desc),
            onDismiss = { showProxyDialog = false },
            onConfirm = {
                viewModel.setProxy(it)
                showProxyDialog = false
            },
        )
    }
}
