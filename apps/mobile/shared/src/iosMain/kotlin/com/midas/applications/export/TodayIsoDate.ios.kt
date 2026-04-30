package com.midas.applications.export

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

@OptIn(ExperimentalForeignApi::class)
actual fun todayIsoDate(): String {
    val df = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd"
        // POSIX locale → independiente de configuración regional del usuario;
        // garantiza el formato ISO sin sorprendernos en tablets con locale "ar".
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
    }
    return df.stringFromDate(NSDate())
}
