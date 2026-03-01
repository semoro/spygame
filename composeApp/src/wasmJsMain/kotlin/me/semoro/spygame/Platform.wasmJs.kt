package me.semoro.spygame

import kotlinx.browser.window

actual fun shouldShowSocialExperiments(): Boolean =
    window.location.hash == "#s"
