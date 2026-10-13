package com.lesego.electrical

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.lesego.electrical.auth.LoginActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for [LoginActivity] using Espresso.
 *
 * These run on a connected device or emulator via:
 *   ./gradlew connectedAndroidTest
 *
 * They test the UI behaviour visible to the user — form validation
 * error messages, button states, and navigation flows.
 *
 * @author Lesego Electrical Team
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LoginActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(LoginActivity::class.java)

    @Test
    fun loginScreen_displaysEmailAndPasswordFields() {
        onView(withId(R.id.etEmail)).check(matches(isDisplayed()))
        onView(withId(R.id.etPassword)).check(matches(isDisplayed()))
        onView(withId(R.id.btnLogin)).check(matches(isDisplayed()))
        onView(withId(R.id.btnGoogleSignIn)).check(matches(isDisplayed()))
    }

    @Test
    fun loginScreen_showsErrorForEmptyEmail() {
        // Leave email blank, enter a password, and press login
        onView(withId(R.id.etPassword)).perform(typeText("password123"), closeSoftKeyboard())
        onView(withId(R.id.btnLogin)).perform(click())

        // Error should appear on the email field
        onView(withId(R.id.tilEmail))
            .check(matches(hasDescendant(withText(R.string.error_email_required))))
    }

    @Test
    fun loginScreen_showsErrorForInvalidEmailFormat() {
        onView(withId(R.id.etEmail)).perform(typeText("notanemail"), closeSoftKeyboard())
        onView(withId(R.id.etPassword)).perform(typeText("password123"), closeSoftKeyboard())
        onView(withId(R.id.btnLogin)).perform(click())

        onView(withId(R.id.tilEmail))
            .check(matches(hasDescendant(withText(R.string.error_email_invalid))))
    }

    @Test
    fun loginScreen_showsErrorForShortPassword() {
        onView(withId(R.id.etEmail)).perform(typeText("user@test.com"), closeSoftKeyboard())
        onView(withId(R.id.etPassword)).perform(typeText("short"), closeSoftKeyboard())
        onView(withId(R.id.btnLogin)).perform(click())

        onView(withId(R.id.tilPassword))
            .check(matches(hasDescendant(withText(R.string.error_password_short))))
    }

    @Test
    fun loginScreen_registerLinkIsClickable() {
        onView(withId(R.id.tvRegister)).check(matches(isClickable()))
    }

    @Test
    fun loginScreen_forgotPasswordLinkIsDisplayed() {
        onView(withId(R.id.tvForgotPassword)).check(matches(isDisplayed()))
    }

    @Test
    fun loginScreen_progressBarHiddenInitially() {
        onView(withId(R.id.progressBar)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }
}
