package ankers.compose.router

import kotlin.jvm.JvmName

interface NavigationRoot : Screen
interface ChildScreenOf<T : Screen> : Screen

interface CombinedNavigation<L : Screen, R : Screen> : Screen {
    val left: Screen
    val right: Screen

    class ParentChild<L : Screen, R : Screen>(
        override val left: Screen,
        override val right: Screen,
    ) : CombinedNavigation<L, R>

    val parent get() = (left as? CombinedNavigation<*, *>)?.right ?: left

    /** Calls `action` for each screen in ascending order (child to parent) */
    fun forEach(action: (parent: Screen?, current: Screen) -> Unit) {

        // This is pretty old/hacked together. I think the idea was to keep
        // iterating the same level until we hit the parent. We want `action`
        // to be called with: a) the screen itself and b) its navigator aka parent
        var current: Screen = this
        val waitingForParent = mutableListOf<Screen>()

        while (current is CombinedNavigation<*, *>) {
            val snapshot = current

            when (snapshot) {
                is ParentChild<*, *> -> {
                    val parent = snapshot.parent

                    waitingForParent.forEach { action(parent, it) }
                    waitingForParent.clear()

                    action(parent, snapshot.right)
                }

                else -> waitingForParent.add(snapshot.right)
            }

            current = snapshot.left
        }

        waitingForParent.asReversed().forEach { action(null, it) }
        action(null, current)
    }

    companion object {
        operator fun <L : Screen, R : Screen> invoke(left: Screen, right: Screen) =
            object : CombinedNavigation<L, R> {
                override val left = left
                override val right = right
            }
    }
}

// AthenA
fun <P, L : ChildScreenOf<P>, R : ChildScreenOf<P>> L.then(same: R): CombinedNavigation<L, R> =
    CombinedNavigation(this, same)

// AthenB
fun <L : Screen, R : ChildScreenOf<L>> L.then(child: R): CombinedNavigation<L, R> =
    CombinedNavigation.ParentChild(this, child)

// ABthenB
@JvmName("ABThenB")
fun <T, L : Screen, M : ChildScreenOf<T>, R : ChildScreenOf<T>> CombinedNavigation<L, M>.then(same: R): CombinedNavigation<L, R> =
    CombinedNavigation(this, same)

// AAthenB
@JvmName("AAThenB")
fun <T, L : Screen, M : ChildScreenOf<T>, R : ChildScreenOf<M>> CombinedNavigation<L, M>.then(child: R): CombinedNavigation<L, R> =
    CombinedNavigation.ParentChild(this, child)
