package net.twinte.mobile_experiments

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.PolymorphicSerializer
import net.twinte.mobile_experiments.core.domain.CourseMethod
import net.twinte.mobile_experiments.core.domain.CourseSchedule
import net.twinte.mobile_experiments.core.domain.Day
import net.twinte.mobile_experiments.core.domain.Module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigatorTest {
    @Test
    fun topLevelSelectionDoesNotBuildATabHistory() {
        val navigator = navigator()
        navigator.selectTab(AppTab.Courses)
        navigator.selectTab(AppTab.Today)
        navigator.selectTab(AppTab.Account)

        assertEquals(AppRoute.Account, navigator.currentRoute)
        assertFalse(navigator.isNested)
        navigator.goBack()
        assertEquals(AppTab.Timetable, navigator.selectedTab)
        navigator.goBack()
        assertEquals(AppRoute.Timetable, navigator.currentRoute)
        assertEquals(1, navigator.stacks.getValue(AppTab.Timetable).size)
    }

    @Test
    fun nestedNavigationIsDeduplicatedAndRetainedWhenSwitchingTabs() {
        val navigator = navigator()
        navigator.openTags()
        navigator.openTags()
        assertTrue(navigator.isNested)
        assertEquals(2, navigator.stacks.getValue(AppTab.Courses).size)

        navigator.selectTab(AppTab.Account)
        navigator.selectTab(AppTab.Courses)
        assertEquals(AppRoute.Tags, navigator.currentRoute)
        navigator.goBack()
        assertEquals(AppRoute.Courses, navigator.currentRoute)
        navigator.goBack()
        assertEquals(AppRoute.Timetable, navigator.currentRoute)
    }

    @Test
    fun everyRouteRoundTripsWithoutPlatformReflection() {
        val serializer = PolymorphicSerializer(NavKey::class)
        val routes = AppTab.entries.map { it.root } + listOf(
            AppRoute.Tags, AppRoute.RegisteredCourses, AppRoute.CourseDetail("course-id"),
            AppRoute.CourseEditor(2026), AppRoute.CourseEditor(2026, "course-id"),
        )
        routes.forEach { route ->
            val saved = encodeToSavedState(serializer, route, appRoutesConfiguration)
            assertEquals(route, decodeFromSavedState(serializer, saved, appRoutesConfiguration))
        }
    }

    @Test
    fun registrationReturnsToTheGridEvenIfAHiddenTimetableDetailIsRetained() {
        val navigator = navigator()
        navigator.openCourseDetail("previous-course")
        navigator.selectTab(AppTab.Courses)
        navigator.openRegisteredCourses()
        navigator.returnToTimetable()
        assertEquals(AppRoute.Timetable, navigator.currentRoute)
        assertEquals(listOf(AppRoute.Timetable), navigator.stacks.getValue(AppTab.Timetable).toList())
        assertEquals(listOf(AppRoute.Courses), navigator.stacks.getValue(AppTab.Courses).toList())
    }

    @Test
    fun editorReturnsToItsOriginAndIsRemovedWithItsCourse() {
        val navigator = navigator()
        navigator.openCourseDetail("course-id")
        navigator.openCourseEditor(2026, "course-id")
        navigator.openCourseEditor(2026, "course-id")
        assertEquals(3, navigator.stacks.getValue(AppTab.Timetable).size)
        navigator.goBack()
        assertEquals(AppRoute.CourseDetail("course-id"), navigator.currentRoute)
        navigator.openCourseEditor(2026, "course-id")
        navigator.dismissCourseScreens("course-id")
        assertEquals(AppRoute.Timetable, navigator.currentRoute)
        navigator.selectTab(AppTab.Courses)
        navigator.openCourseEditor(2026)
        navigator.dismissCourseScreens()
        assertEquals(AppRoute.Courses, navigator.currentRoute)
    }

    @Test
    fun editorDraftRoundTripsAllEditableFields() {
        val draft = CourseEditorDraft(
            year = 2026,
            name = "Seminar",
            instructors = listOf("Teacher", ""),
            credit = "1.5",
            methods = setOf(CourseMethod.FaceToFace),
            schedules = listOf(CourseSchedule(Module.SpringA, Day.Monday, 2, "3A204")),
            rooms = listOf(CourseEditorRoom("研究室", setOf(0)), CourseEditorRoom("", emptySet())),
            memo = "Bring notes",
            tagIds = setOf("tag-id"),
        )
        assertEquals(draft, decodeFromSavedState<CourseEditorDraft>(encodeToSavedState(draft)))
    }

    private fun navigator() = AppNavigator(
        selection = mutableStateOf(AppTab.Timetable.name),
        stacks = AppTab.entries.associateWith { NavBackStack<NavKey>(it.root) },
    )
}
