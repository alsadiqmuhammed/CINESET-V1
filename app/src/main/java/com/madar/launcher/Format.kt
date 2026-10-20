package com.madar.launcher

import java.util.Locale

/** Formats with Latin digits regardless of the phone's Arabic locale; every number in Madar is set in mono. */
fun String.fmt(vararg args: Any?): String = String.format(Locale.ROOT, this, *args)
