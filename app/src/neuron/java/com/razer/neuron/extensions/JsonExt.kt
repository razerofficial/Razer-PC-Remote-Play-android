package com.razer.neuron.extensions
import com.google.gson.Gson

val defaultJson get() = com.limelight.utils.defaultJson
val defaultJsonPrettyPrint get() = defaultJson.newBuilder().apply { setPrettyPrinting() }.create()