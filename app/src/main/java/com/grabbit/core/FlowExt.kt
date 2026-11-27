package com.grabbit.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

fun <T> Flow<T>.stateInUi(
    scope: CoroutineScope,
    initial: T,
): StateFlow<T> =
    stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(Timing.FLOW_SUBSCRIPTION_GRACE.inWholeMilliseconds),
        initialValue = initial,
    )
