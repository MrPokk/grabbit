package com.grabbit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grabbit.R
import com.grabbit.ui.theme.Dimens

/** Entry screen for pasting a link, previewing the media and starting a download. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    incomingLink: String?,
    onLinkConsumed: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(incomingLink) {
        if (!incomingLink.isNullOrBlank()) {
            viewModel.acceptSharedLink(incomingLink)
            onLinkConsumed()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.home_title)) },
            scrollBehavior = scrollBehavior,
        )

        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .weight(1f)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    start = Dimens.screenHorizontal,
                    end = Dimens.screenHorizontal,
                    top = Dimens.xxs,
                ),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            homeMediaContent(state, settings, viewModel)
        }

        UrlCard(
            url = state.url,
            loading = state.loading,
            clipboardHint = state.clipboardHint,
            onUrlChange = viewModel::onUrlChange,
            onClear = viewModel::clear,
            onPaste = viewModel::pasteFromClipboard,
            onFetch = viewModel::fetch,
            modifier = Modifier.padding(horizontal = Dimens.screenHorizontal),
        )
    }

    val media = state.media
    if (state.showSheet && media != null) {
        FormatSheet(
            media = media,
            defaults = settings,
            onDismiss = viewModel::dismissSheet,
            onConfirm = viewModel::startDownload,
        )
    }
}
