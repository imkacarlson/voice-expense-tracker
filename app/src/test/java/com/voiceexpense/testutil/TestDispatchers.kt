package com.voiceexpense.testutil

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
object TestDispatchers {
    val io: CoroutineDispatcher = StandardTestDispatcher()
    val default: CoroutineDispatcher = StandardTestDispatcher()
}

