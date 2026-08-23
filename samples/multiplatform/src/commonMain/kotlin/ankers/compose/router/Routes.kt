package ankers.compose.router

import kotlinx.serialization.Serializable

@Serializable object Root : NavigationRoot

@Serializable object SignedIn : ChildScreenOf<Root>
@Serializable object SignedOut : ChildScreenOf<Root>

// global overlays: owned by the root, reachable from anywhere
@Serializable object ConnectSheet : ChildScreenOf<Root>
@Serializable object ConfirmDialog : ChildScreenOf<Root>

@Serializable object ModalStack : ChildScreenOf<Root>
@Serializable object ModalA : ChildScreenOf<ModalStack>
@Serializable object ModalB : ChildScreenOf<ModalStack>
@Serializable object ModalC : ChildScreenOf<ModalStack>

@Serializable object TabA : ChildScreenOf<SignedIn>
@Serializable object TabB : ChildScreenOf<SignedIn>
@Serializable object TabC : ChildScreenOf<SignedIn>
@Serializable object TabD : ChildScreenOf<SignedIn>

@Serializable object NestedTabA : ChildScreenOf<TabB>
@Serializable object NestedTabB : ChildScreenOf<TabB>

@Serializable object DeepDestinationA : ChildScreenOf<NestedTabB>
@Serializable data class DeepDestinationB(val arg: String) : ChildScreenOf<NestedTabB>

@Serializable object StackNavigatorExample : ChildScreenOf<TabC>

// list/detail, with a sheet owned by the section that opens it
@Serializable object Items : ChildScreenOf<TabD>
@Serializable data class ItemDetail(val id: Int) : ChildScreenOf<TabD>
@Serializable data class ItemActions(val id: Int) : ChildScreenOf<TabD>
