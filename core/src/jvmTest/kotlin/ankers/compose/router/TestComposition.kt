package ankers.compose.router

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import androidx.compose.ui.test.frameDelayMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

// a composition without ui: recomposer + no-op applier, frames driven by the test scheduler
@OptIn(ExperimentalTestApi::class)
class TestComposition(private val scope: TestScope) {
    private val clock = TestMonotonicFrameClock(scope)
    private var composition: Composition? = null
    private var snapshotHandle: ObserverHandle? = null
    var failure: Throwable? = null
        private set

    fun setContent(content: @Composable () -> Unit) {
        val recomposer = Recomposer(scope.backgroundScope.coroutineContext + clock)
        val composition = Composition(UnitApplier, recomposer).also { this.composition = it }

        scope.backgroundScope.launch(clock, start = CoroutineStart.UNDISPATCHED) {
            try {
                recomposer.runRecomposeAndApplyChanges()
            } catch (e: CancellationException) {
                composition.dispose()
                snapshotHandle?.dispose()
            } catch (e: Throwable) {
                failure = e
            }
        }

        var applyScheduled = false
        snapshotHandle = Snapshot.registerGlobalWriteObserver {
            if (!applyScheduled) {
                applyScheduled = true
                scope.backgroundScope.launch {
                    applyScheduled = false
                    Snapshot.sendApplyNotifications()
                }
            }
        }

        try {
            composition.setContent(content)
        } catch (e: Throwable) {
            failure = e
        }
    }

    /** Advances frames until [cond] holds or [frames] elapse. Rethrows any composition failure. */
    fun waitUntil(frames: Int = 100, cond: () -> Boolean) {
        repeat(frames) {
            failure?.let { throw it }
            if (cond()) return
            frame()
        }
        failure?.let { throw it }
        check(cond()) { "condition not met after $frames frames" }
    }

    fun frame() {
        scope.advanceTimeBy(clock.frameDelayMillis)
        scope.runCurrent()
        failure?.let { throw it }
    }

    fun frames(count: Int) = repeat(count) { frame() }

    fun dispose() {
        composition?.dispose()
        snapshotHandle?.dispose()
    }

    private object UnitApplier : AbstractApplier<Unit>(Unit) {
        override fun insertBottomUp(index: Int, instance: Unit) {}
        override fun insertTopDown(index: Int, instance: Unit) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun remove(index: Int, count: Int) {}
        override fun onClear() {}
    }
}
