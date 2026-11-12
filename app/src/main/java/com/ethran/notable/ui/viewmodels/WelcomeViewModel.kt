package com.ethran.notable.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.db.KvProxy
import com.ethran.notable.i18n.AppLanguage
import com.ethran.notable.i18n.LanguagePrefs
import com.ethran.notable.planner.PlannerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val kvProxy: KvProxy,
    private val plannerService: PlannerService,
) : ViewModel() {

    suspend fun removeWelcome() {
        kvProxy.setAppSettings(
            GlobalAppSettings.current.copy(showWelcome = false)
        )
    }

    /**
     * Same as in Settings: saves the app language and renames the planner to it, then [onDone] restarts the app.
     * The welcome page is marked to open again after the restart, so the user does not lose their place.
     */
    fun setAppLanguage(context: Context, language: AppLanguage, onDone: () -> Unit) {
        LanguagePrefs.setApp(context, language)
        LanguagePrefs.setResumeWelcome(context, true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { plannerService.relocalize() }
            withContext(Dispatchers.Main) { onDone() }
        }
    }
}
