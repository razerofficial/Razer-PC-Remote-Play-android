package com.razer.neuron.provider.sources

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.limelight.BuildConfig
import com.razer.neuron.extensions.defaultJson
import com.razer.neuron.model.VideoFormatMetaData
import com.razer.neuron.provider.NeuronContentSource
import com.razer.neuron.provider.ObjectContentSource
import com.razer.neuron.shared.SharedConstants
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import timber.log.Timber

class NeuronMetaDataSource : NeuronContentSource {
    override val path: String
        get() = SharedConstants.NEURON_META_DATA

    override fun query(
        context: Context,
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cols = projection ?: arrayOf()
        val values = cols.map {
                col ->
            when(col) {
                SharedConstants.BUILD_VERSION_CODE -> BuildConfig.VERSION_CODE
                SharedConstants.VIDEO_FORMAT_JSON -> defaultJson.toJson(VideoFormatMetaData.create(context))
                else -> null
            }
        }
        return MatrixCursor(cols).apply {
            addRow(values)
        }
    }

    override fun insert(context: Context, uri: Uri, values: ContentValues?): Uri? {
        error("Cannot write into NeuronMetaDataSource")
    }

    override fun delete(
        context: Context,
        uri: Uri,
        whereClause: String?,
        whereArgs: Array<out String>?
    ): Int {
        error("Cannot write into NeuronMetaDataSource")
    }

    override fun update(
        context: Context,
        uri: Uri,
        values: ContentValues?,
        whereClause: String?,
        whereArgs: Array<out String>?
    ): Int {
        error("Cannot write into NeuronMetaDataSource")
    }
}