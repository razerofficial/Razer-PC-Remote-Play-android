package com.razer.neuron.provider.sources

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.limelight.computers.ComputerDatabaseManager
import com.razer.neuron.provider.ComputerSharedModelHelper
import com.limelight.nvstream.http.ComputerDetails
import com.razer.neuron.managers.AnalyticsManager
import com.razer.neuron.managers.SimpleDbManager
import com.razer.neuron.managers.Table
import com.razer.neuron.model.NeuronEvent
import com.razer.neuron.model.NeuronEventFields
import com.razer.neuron.provider.DaoContentProvider
import com.razer.neuron.provider.NeuronContentSource
import com.razer.neuron.shared.SharedConstants

/**
 * [NeuronContentSource] for [NeuronEvent]
 *
 * [NeuronEventsSource] is read only, we don't expect other apps (even Nexus) to insert
 * data to this source
 */
class NeuronEventsSource : NeuronContentSource {

    override val path = SharedConstants.NEURON_EVENTS

    override fun query(
        context: Context,
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ) = SimpleDbManager.readCursor(Table.NeuronEventsTable.name).getOrNull()

    override fun insert(context: Context, uri: Uri, values: ContentValues?) = error("Insert not supported")

    override fun delete(
        context: Context,
        uri: Uri,
        whereClause: String?,
        whereArgs: Array<out String>?
    ) = SimpleDbManager.deleteWhere(Table.NeuronEventsTable.name, whereClause, whereArgs).getOrNull() ?: 0

    override fun update(
        context: Context,
        uri: Uri,
        values: ContentValues?,
        whereClause: String?,
        whereArgs: Array<out String>?
    ) = error("Update not supported")
}



