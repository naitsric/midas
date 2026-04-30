package com.midas.applications.export

import java.time.LocalDate

actual fun todayIsoDate(): String = LocalDate.now().toString()
