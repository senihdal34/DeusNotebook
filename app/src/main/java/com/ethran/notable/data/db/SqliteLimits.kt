package com.ethran.notable.data.db

/**
 * Largest number of bound variables we put in one `IN (:list)` query. SQLite's default
 * limit is 999 on the Android versions we support (minSdk 29), so stay a little below it.
 */
const val SQLITE_VARIABLE_CHUNK = 900
