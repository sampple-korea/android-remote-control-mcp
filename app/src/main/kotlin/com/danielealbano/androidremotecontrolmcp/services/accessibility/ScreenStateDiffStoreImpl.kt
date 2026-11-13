package com.danielealbano.androidremotecontrolmcp.services.accessibility

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenStateDiffStoreImpl
    @Inject
    constructor() : ScreenStateDiffStore {
        @Volatile
        private var baseline: ScreenStateRows? = null

        override fun replace(rows: ScreenStateRows): ScreenStateRows? =
            synchronized(this) {
                val previous = baseline
                baseline = rows
                previous
            }

        override fun clear() {
            synchronized(this) {
                baseline = null
            }
        }
    }
