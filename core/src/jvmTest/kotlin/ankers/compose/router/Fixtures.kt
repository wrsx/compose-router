package ankers.compose.router

import kotlinx.serialization.Serializable

import androidx.lifecycle.ViewModelStore
import ankers.compose.router.entry.EntryId
import ankers.compose.router.entry.NavEntry
import ankers.compose.router.host.OwnerEntry
import ankers.compose.router.events.EntryEvents
import ankers.compose.router.events.HostEvents
import ankers.compose.router.events.HostState
import ankers.compose.router.events.NavigatorEvents
import ankers.compose.router.host.JvmOwnerPlatform
import ankers.compose.router.navigator.NavigationRuntime
import ankers.compose.router.render.RouterRenderer

@Serializable object Root : NavigationRoot
@Serializable object A : ChildScreenOf<Root>
@Serializable object B : ChildScreenOf<Root>
@Serializable data class C(val n: Int) : ChildScreenOf<Root>
@Serializable object Sheet : ChildScreenOf<Root>
@Serializable object Tabs : ChildScreenOf<Root>
@Serializable object SignedOut : ChildScreenOf<Root>

@Serializable object TabA : ChildScreenOf<Tabs>
@Serializable object TabB : ChildScreenOf<Tabs>
@Serializable object TabC : ChildScreenOf<Tabs>

@Serializable object Deep : ChildScreenOf<TabB>
@Serializable data class Deeper(val id: String) : ChildScreenOf<TabB>
@Serializable object LocalSheet : ChildScreenOf<TabB>

sealed interface Overlay : ChildScreenOf<Root>
@Serializable object Overlay1 : Overlay
@Serializable object Overlay2 : Overlay

/** Renders only the selected entry, with no animation and no layout nodes: what the bare harness can compose. */
val PlainRenderer: RouterRenderer = { inlineSelected?.let { render(it) } }

/** Records every event as one line, e.g. `entry root/1 A`, `state root/1 Active`, `retired root/1`. */
class RecordingEvents : NavigatorEvents {
    val log = mutableListOf<String>()

    override fun entry(entry: NavEntry<*>): EntryEvents {
        log += "entry ${entry.id} ${entry.screen::class.simpleName}"
        return object : EntryEvents {
            override fun hosted(): HostEvents {
                log += "hosted ${entry.id}"
                return object : HostEvents {
                    override fun state(state: HostState) { log += "state ${entry.id} $state" }
                    override fun unhosted() { log += "unhosted ${entry.id}" }
                }
            }

            override fun child(): NavigatorEvents = this@RecordingEvents
            override fun retired() { log += "retired ${entry.id}" }
        }
    }

    fun of(prefix: String) = log.filter { it.startsWith(prefix) }
    fun retired() = of("retired ").map { it.removePrefix("retired ") }
}

fun testRuntime(): NavigationRuntime = NavigationRuntime(JvmOwnerPlatform)

fun id(path: String) = EntryId.parse(path)

fun ownerEntry(path: String, screen: Screen) = OwnerEntry(id(path), screen, ViewModelStore(), JvmOwnerPlatform).also { it.restoreState(null) }
