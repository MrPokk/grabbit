package com.grabbit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import com.grabbit.R
import com.grabbit.core.AppError
import com.grabbit.core.UiValues
import com.grabbit.ui.theme.Dimens
import com.grabbit.ui.theme.Motion
import kotlin.math.roundToInt

@Composable
fun Thumbnail(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconMd),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.cd_thumbnail),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val slidePx = (Motion.EmptyStateSlideDp.value * density.density).roundToInt()
    AnimatedVisibility(
        visible = true,
        enter =
            fadeIn(animationSpec = Motion.EmptyStateSpec) +
                slideInVertically(animationSpec = Motion.EmptyStateSlideSpec) { _ -> slidePx },
        exit =
            fadeOut(animationSpec = Motion.EmptyStateSpec) +
                slideOutVertically(animationSpec = Motion.EmptyStateSlideSpec) { _ -> slidePx },
    ) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(Dimens.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconLg),
            )
            Spacer(Modifier.height(Dimens.md))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Dimens.xs))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(Dimens.lg))
                action()
            }
        }
    }
}

@Composable
fun ErrorCard(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onUpdate: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Column(Modifier.padding(Dimens.md)) {
            Text(
                text = stringResource(R.string.error_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Dimens.xxs))
            Text(
                text = stringResource(error.messageRes),
                style = MaterialTheme.typography.bodyMedium,
            )
            error.technical?.takeIf { it.isNotBlank() }?.let { technical ->
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text =
                            stringResource(
                                if (expanded) R.string.error_details_hide else R.string.error_details_show,
                            ),
                    )
                }
                if (expanded) {
                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn(animationSpec = Motion.StatusChangeSpec),
                        exit = fadeOut(animationSpec = Motion.StatusChangeSpec),
                    ) {
                        Text(
                            text = technical,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = UiValues.DETAILS_MAX_LINES,
                        )
                    }
                }
            }
            if (onRetry != null || (onUpdate != null && error.suggestUpdate)) {
                Row(
                    modifier = Modifier.padding(top = Dimens.xs),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                ) {
                    if (onRetry != null) {
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                    if (onUpdate != null && error.suggestUpdate) {
                        TextButton(onClick = onUpdate) {
                            Text(stringResource(R.string.action_update_ytdlp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Dimens.md, end = Dimens.md, top = Dimens.lg, bottom = Dimens.xxs),
    )
}
