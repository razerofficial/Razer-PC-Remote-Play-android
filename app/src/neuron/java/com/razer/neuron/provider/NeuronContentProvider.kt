package com.razer.neuron.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.razer.neuron.RnApp
import com.razer.neuron.common.logAndRecordException
import com.razer.neuron.provider.sources.NeuronAndroidCryptoSource
import com.razer.neuron.provider.sources.NeuronComputerDetailsSource
import com.razer.neuron.provider.sources.NeuronMetaDataSource
import com.razer.neuron.provider.sources.RemotePlaySettingsProviderSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * A implementation of [ContentProvider] for other apps to use Neuron's data.
 * Lots of sensitive information are in the app. Permission must set as dangerous
 */
class NeuronContentProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SharedContentProviderEntryPoint {
        fun remotePlaySettingsSource(): RemotePlaySettingsProviderSource
        fun androidCryptoProviderSource(): NeuronAndroidCryptoSource
        fun computerDetailsSource(): NeuronComputerDetailsSource
        fun metaDataSource(): NeuronMetaDataSource
    }


    private val dependency by lazy {
        val ctx = (context ?: RnApp.appContext)
        EntryPointAccessors.fromApplication(ctx, SharedContentProviderEntryPoint::class.java)
    }

    /**
     * Add your instance of [NeuronContentSource] here
     */
    private val sources: List<NeuronContentSource> by lazy {
        listOf(
            dependency.remotePlaySettingsSource(),
            dependency.androidCryptoProviderSource(),
            dependency.computerDetailsSource(),
            dependency.metaDataSource()
        )
    }

    private val sourcesMap by lazy { sources.associateBy { it.path } }

    private val supportedPaths by lazy { sources.map { it.path } }

    private fun <T> Uri.runOnSource(task: (source: NeuronContentSource) -> T?): T? {
        val path = lastPathSegment
        val source = sourcesMap[path] ?: throw SharedContentProviderException("Source not created for ${path}")
        return try {
            task(source)
        } catch (t: Throwable) {
            logAndRecordException(t, "uri" to this.toString())
            null
        }
    }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val context = (context ?: RnApp.appContext)
        return uri.runOnSource { source ->
            source.query(context, uri, projection, selection, selectionArgs, sortOrder)
        }
    }

    override fun getType(uri: Uri): String {
        if (supportedPaths.any { supportedPath -> (uri.path ?: "").contains(supportedPath) }) {
            return "vnd.android.cursor.dir/${uri.lastPathSegment}"
        }
        throw IllegalStateException("Unsupported uri $uri")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val context = (context ?: RnApp.appContext)
        return uri.runOnSource { source ->
            source.insert(context, uri, values)
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val context = (context ?: RnApp.appContext)
        return uri.runOnSource { source ->
            source.delete(context, uri, whereClause = selection, whereArgs = selectionArgs)
        } ?: 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val context = (context ?: RnApp.appContext)
        return uri.runOnSource { source ->
            source.update(context, uri, values, whereClause = selection, whereArgs = selectionArgs)
        } ?: 0
    }

}


