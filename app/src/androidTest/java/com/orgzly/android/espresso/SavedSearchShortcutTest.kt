package com.orgzly.android.espresso

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.RetryTestRule
import com.orgzly.android.espresso.util.EspressoUtils.onNotesInSearch
import com.orgzly.android.espresso.util.EspressoUtils.onSnackbar
import com.orgzly.android.espresso.util.EspressoUtils.recyclerViewItemCount
import com.orgzly.android.ui.SavedSearchChooserActivity
import com.orgzly.android.ui.main.MainActivity
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SavedSearchShortcutTest : OrgzlyTest() {
    @get:Rule
    val retryTestRule = RetryTestRule()

    @Before
    override fun setUp() {
        super.setUp()

        testUtils.setupBook("book-a", "* Note A :work:\n* Note B\n* Note C :work:")
        testUtils.createSavedSearch("Work", "t.work")
    }

    @Test
    fun testShortcutOpensSearchResults() {
        ActivityScenario.launch<MainActivity>(createShortcut())

        onNotesInSearch().check(matches(recyclerViewItemCount(2)))
    }

    @Test
    fun testShortcutFollowsEditedSearch() {
        val shortcut = createShortcut()
        dataRepository.updateSavedSearch(savedSearch().copy(query = "b.book-a"))

        ActivityScenario.launch<MainActivity>(shortcut)

        onNotesInSearch().check(matches(recyclerViewItemCount(3)))
    }

    @Test
    fun testShortcutToDeletedSearch() {
        val shortcut = createShortcut()
        dataRepository.deleteSavedSearches(setOf(savedSearch().id))

        ActivityScenario.launch<MainActivity>(shortcut)

        onSnackbar().check(matches(withText(R.string.saved_search_does_not_exist_anymore)))
    }

    @Test
    fun testShortcutForSearchWithoutName() {
        testUtils.createSavedSearch("", "b.book-a")

        val result = chooseSavedSearch("b.book-a")

        assertThat(result.getStringExtra(Intent.EXTRA_SHORTCUT_NAME), `is`("Search"))
    }

    private fun savedSearch() = dataRepository.getSavedSearchesByNameIgnoreCase("Work").single()

    private fun createShortcut(): Intent {
        val result = chooseSavedSearch("Work")

        assertThat(result.getStringExtra(Intent.EXTRA_SHORTCUT_NAME), `is`("Work"))

        return IntentCompat.getParcelableExtra(
            result, Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)!!
    }

    private fun chooseSavedSearch(text: String): Intent {
        val chooser = Intent(context, SavedSearchChooserActivity::class.java)
            .setAction(Intent.ACTION_CREATE_SHORTCUT)
        val scenario = ActivityScenario.launchActivityForResult<SavedSearchChooserActivity>(chooser)

        onView(withText(text)).perform(click())

        val result = scenario.result
        assertThat(result.resultCode, `is`(RESULT_OK))
        return result.resultData
    }
}
