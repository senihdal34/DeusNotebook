// SPDX-License-Identifier: AGPL-3.0-only
// Reading board design derived from BookOrbit (https://github.com/bookorbit/bookorbit), AGPL-3.0-only with additional terms, see licenses/bookorbit/
package com.ethran.notable.reading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** Starts a job unless the one it started last is still running. */
class OneAtATime {
    private var job: Job? = null

    /** The new job, or null when the last one is still active (then nothing is started). */
    @Synchronized
    fun launch(scope: CoroutineScope, context: CoroutineContext, block: suspend CoroutineScope.() -> Unit): Job? {
        if (job?.isActive == true) return null
        return scope.launch(context, block = block).also { job = it }
    }
}
