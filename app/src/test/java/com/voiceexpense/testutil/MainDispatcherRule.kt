package com.voiceexpense.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val _scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(_scheduler)
) : TestWatcher() {

    val scheduler: TestCoroutineScheduler get() = _scheduler

    override fun starting(description: Description?) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description?) {
        Dispatchers.resetMain()
    }

    fun advanceUntilIdle() { _scheduler.advanceUntilIdle() }
    fun runCurrent() { _scheduler.runCurrent() }
}
