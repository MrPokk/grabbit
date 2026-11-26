package com.grabbit.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.grabbit.R
import com.grabbit.data.download.MediaType
import com.grabbit.ui.theme.Dimens

private const val MEDIA_TYPE_OPTIONS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDownloadControls(
    mediaType: MediaType,
    enabled: Boolean,
    onMediaTypeChange: (MediaType) -> Unit,
    onQuickDownload: () -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mediaType == MediaType.VIDEO,
            onClick = { onMediaTypeChange(MediaType.VIDEO) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = MEDIA_TYPE_OPTIONS),
        ) {
            Text(stringResource(R.string.tab_video))
        }
        SegmentedButton(
            selected = mediaType == MediaType.AUDIO,
            onClick = { onMediaTypeChange(MediaType.AUDIO) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = MEDIA_TYPE_OPTIONS),
        ) {
            Text(stringResource(R.string.tab_audio))
        }
    }

    Button(
        onClick = onQuickDownload,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Icon(Icons.Rounded.Download, contentDescription = null)
        Spacer(Modifier.width(Dimens.xs))
        Text(
            text = stringResource(R.string.action_quick_download),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
