package com.ethran.notable.ui.views

/** The steps of the first-run page, in the order they are shown. */
enum class WelcomeStep(val required: Boolean) {
    LANGUAGE(false),
    FILE_ACCESS(true),
    REFRESH_MODE(false),
    UNFREEZE(false),
    SYNC(false),
    GUIDE(false);

    /** 1-based number shown to the user. */
    val number: Int get() = ordinal + 1
}

/** What the system says right now; read again whenever the screen comes back to the front. */
data class WelcomeProgress(
    val fileAccessGranted: Boolean,
    val refreshModeRecommended: Boolean,
)

enum class StepStatus { DONE, TODO, INFO }

/**
 * DONE / TODO for the steps the app can check itself (file access, refresh mode); INFO for the steps that are
 * only reading or a choice (language, unfreeze, sync, guide): the app cannot tell if they are finished.
 */
fun stepStatus(step: WelcomeStep, progress: WelcomeProgress): StepStatus = when (step) {
    WelcomeStep.FILE_ACCESS -> if (progress.fileAccessGranted) StepStatus.DONE else StepStatus.TODO
    WelcomeStep.REFRESH_MODE -> if (progress.refreshModeRecommended) StepStatus.DONE else StepStatus.TODO
    else -> StepStatus.INFO
}

/** Required steps that are still open. Continue is enabled when this is empty. */
fun remainingRequired(progress: WelcomeProgress): List<WelcomeStep> =
    WelcomeStep.entries.filter { it.required && stepStatus(it, progress) != StepStatus.DONE }

fun canContinue(progress: WelcomeProgress): Boolean = remainingRequired(progress).isEmpty()

/** Sync writes into the notes folder, so it waits for file access. */
fun canSetUpSync(progress: WelcomeProgress): Boolean = progress.fileAccessGranted

/**
 * Where the app opens. The welcome page opens on a first run, without file access, and after a language change that
 * was made on it (the app restarts, and the page must come back with the choice made).
 */
fun startsOnWelcome(showWelcome: Boolean, hasFilePermission: Boolean, resumeWelcome: Boolean): Boolean =
    showWelcome || !hasFilePermission || resumeWelcome
