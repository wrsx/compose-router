package ankers.compose.router

object Root : NavigationRoot

object SignedIn : ChildScreenOf<Root>
object SignedOut : ChildScreenOf<Root>

object ModalStack : ChildScreenOf<Root>
object ModalA : ChildScreenOf<ModalStack>
object ModalB : ChildScreenOf<ModalStack>
object ModalC : ChildScreenOf<ModalStack>
object ModalD : ChildScreenOf<ModalStack>
object ModalE : ChildScreenOf<ModalStack>

object TabA : ChildScreenOf<SignedIn>
object TabB : ChildScreenOf<SignedIn>
object TabC : ChildScreenOf<SignedIn>
object TabD : ChildScreenOf<SignedIn>

object NestedTabA : ChildScreenOf<TabB>
object NestedTabB : ChildScreenOf<TabB>

object DeepDestinationA : ChildScreenOf<NestedTabB>
class DeepDestinationB(val arg: String) : ChildScreenOf<NestedTabB>

object StackNavigatorExample : ChildScreenOf<TabC>