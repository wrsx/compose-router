package ankers.compose.router.navigator

sealed interface NavConfig {

    /** A stack of screens, permitting duplicates */
    object Stack : NavConfig

    /** Maximum one screen instance per route */
    data class Tab(
        val backPress: BackPress = BackPress.Stack,
    ) : NavConfig {

        enum class BackPress {
            /** Navigate to the first tab positionally */
            First,

            /** Maintain a stack of tab history */
            Stack
        }
    }
}