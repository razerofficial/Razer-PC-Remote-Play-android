package com.razer.neuron.common

import android.os.Build
import android.os.Process
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

class CustomerSupportHelper {

    companion object {

        const val TAG = "CustomerSupportHelper"

        fun getLogs(): String? {
            return try {
                val pids = getProcessIds()
                val process = Runtime.getRuntime().exec("logcat -d -v threadtime")
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    val logs = reader.lineSequence()
                        .filter { line -> pids.any { line.contains(it) } }
                        .joinToString(separator = "\n")
                    "Manufacturer: ${Build.MANUFACTURER}\nModel: ${Build.MODEL}\n$logs"
                }
            } catch (exception: Exception) {
                Timber.e("$TAG, Error reading logs, $exception")
                null
            }
        }

        private fun getProcessIds(): List<String> {
            return try {
                val process = Runtime.getRuntime().exec("logcat -d -v process")
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    val ids = reader.lineSequence()
                        .filter { it.contains("*** Session Start ***") }
                        .mapNotNull { line ->
                            Regex("[0-9]+").find(line)?.value
                        }
                        .toList()
                    ids + Process.myPid().toString()
                }
            } catch (exception: Exception) {
                Timber.e("$TAG, Error reading logs, $exception")
                emptyList()
            }
        }
    }
}