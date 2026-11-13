package net.twinte.mobile_experiments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
internal sealed interface AppRoute : NavKey {
    @Serializable data object Timetable : AppRoute
    @Serializable data object Today : AppRoute
    @Serializable data object Courses : AppRoute
    @Serializable data object Account : AppRoute
    @Serializable data object Tags : AppRoute
    @Serializable data object RegisteredCourses : AppRoute
    @Serializable data class CourseDetail(val courseId: String) : AppRoute
    @Serializable data class CourseEditor(val year: Int, val courseId: String? = null) : AppRoute
}

internal enum class AppTab(val root: AppRoute, val title: String) {
    Timetable(AppRoute.Timetable, "ホーム"),
    Today(AppRoute.Today, "今日の時間割"),
    Courses(AppRoute.Courses, "授業の追加"),
    Account(AppRoute.Account, "アカウント"),
}

// Explicit registration avoids reflection-only route restoration on iOS.
internal val appRoutesConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(AppRoute.Timetable::class, AppRoute.Timetable.serializer())
            subclass(AppRoute.Today::class, AppRoute.Today.serializer())
            subclass(AppRoute.Courses::class, AppRoute.Courses.serializer())
            subclass(AppRoute.Account::class, AppRoute.Account.serializer())
            subclass(AppRoute.Tags::class, AppRoute.Tags.serializer())
            subclass(AppRoute.RegisteredCourses::class, AppRoute.RegisteredCourses.serializer())
            subclass(AppRoute.CourseDetail::class, AppRoute.CourseDetail.serializer())
            subclass(AppRoute.CourseEditor::class, AppRoute.CourseEditor.serializer())
        }
    }
}

@Composable
internal fun rememberAppNavigator(): AppNavigator {
    val selectedTab = rememberSaveable { mutableStateOf(AppTab.Timetable.name) }
    val stacks = AppTab.entries.associateWith { tab ->
        rememberNavBackStack(appRoutesConfiguration, tab.root)
    }
    return remember(selectedTab, stacks) { AppNavigator(selectedTab, stacks) }
}

internal class AppNavigator(
    private val selection: MutableState<String>,
    internal val stacks: Map<AppTab, NavBackStack<NavKey>>,
) {
    val selectedTab: AppTab get() = AppTab.valueOf(selection.value)
    val currentRoute: AppRoute get() = stacks.getValue(selectedTab).last() as AppRoute
    val isNested: Boolean get() = stacks.getValue(selectedTab).size > 1

    fun selectTab(tab: AppTab) {
        selection.value = tab.name
    }

    fun openTags() {
        selectTab(AppTab.Courses)
        val stack = stacks.getValue(AppTab.Courses)
        if (stack.last() != AppRoute.Tags) stack.add(AppRoute.Tags)
    }

    fun openCourseSearch() {
        selectTab(AppTab.Courses)
        val stack = stacks.getValue(AppTab.Courses)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun openRegisteredCourses() {
        val stack = stacks.getValue(selectedTab)
        if (stack.last() != AppRoute.RegisteredCourses) stack.add(AppRoute.RegisteredCourses)
    }

    fun returnToTimetable() {
        listOf(AppTab.Courses, AppTab.Timetable).forEach { tab ->
            val stack = stacks.getValue(tab)
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
        selectTab(AppTab.Timetable)
    }

    fun openCourseDetail(courseId: String) {
        val stack = stacks.getValue(selectedTab)
        val route = AppRoute.CourseDetail(courseId)
        if (stack.last() != route) stack.add(route)
    }

    fun openCourseEditor(year: Int, courseId: String? = null) {
        val stack = stacks.getValue(selectedTab)
        val route = AppRoute.CourseEditor(year, courseId)
        if (stack.last() != route) stack.add(route)
    }

    fun dismissCourseScreens(courseId: String? = null) {
        stacks.values.forEach { stack ->
            stack.removeAll {
                when (it) {
                    is AppRoute.CourseDetail -> courseId == null || it.courseId == courseId
                    is AppRoute.CourseEditor -> courseId == null || it.courseId == courseId
                    else -> false
                }
            }
        }
    }

    fun goBack() {
        val stack = stacks.getValue(selectedTab)
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        } else {
            selectTab(AppTab.Timetable)
        }
    }

    @Composable
    fun entries(provider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        // Retain an entry's saveable state even while its tab is not displayed.
        val entriesByTab = stacks.mapValues { (tab, stack) ->
            key(tab) {
                rememberDecoratedNavEntries(
                    backStack = stack,
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                    entryProvider = provider,
                )
            }
        }
        val visibleTabs = if (selectedTab == AppTab.Timetable) {
            listOf(AppTab.Timetable)
        } else {
            listOf(AppTab.Timetable, selectedTab)
        }
        return visibleTabs.flatMap { entriesByTab.getValue(it) }
    }
}
