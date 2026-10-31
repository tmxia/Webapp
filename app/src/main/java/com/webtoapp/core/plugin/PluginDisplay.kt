package com.webtoapp.core.plugin

import com.webtoapp.core.i18n.Strings


fun Plugin.localizedName(): String =
    if (builtIn) Strings.builtinPluginName(id, name) else name

fun Plugin.localizedDescription(): String =
    if (builtIn) Strings.builtinPluginDescription(id, description) else description
