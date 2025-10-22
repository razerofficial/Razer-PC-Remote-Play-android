package com.razer.neuron.managers

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.razer.neuron.RnApp
import com.razer.neuron.model.NeuronEventFields

/**
 * A simple single to manage all [Table] of this app (not including [ComputerDatabaseManager])
 */
object SimpleDbManager {
    private const val RN_DB_NAME: String = "rn_simple_database1.db"

    private val context by lazy { RnApp.appContext }
    private val db: SQLiteDatabase

    init {
        db = openOrCreateDatabase(RN_DB_NAME)
        initTables()
    }

    private fun openOrCreateDatabase(databaseName: String): SQLiteDatabase {
        return try {
            // Create or open an existing DB
            context.openOrCreateDatabase(databaseName, 0, null)
        } catch (e: SQLiteException) {
            // Delete the DB and try again
            context.deleteDatabase(databaseName)
            context.openOrCreateDatabase(databaseName, 0, null)
        }
    }

    /**
     * Execute all SQLs needed for [Table.all]
     *
     * Any new columns (marked with [Column.isAlterColumn]) will be used to invoke
     * [addColumnIfNotExist]
     */
    private fun initTables() {
        Table.all.forEach { table ->
            createTableIfNotExist(table.name, *table.columns)
            table.columns.filter { it.isAlterColumn }.forEach {
                alterColumn ->
                addColumnIfNotExist(table.name, alterColumn)
            }
        }
    }

    private fun createTableIfNotExist(
        tableName: String,
        vararg columns: Column
    ) = kotlin.runCatching {
        // Create tables if they aren't already there
        val sql =
            "CREATE TABLE IF NOT EXISTS $tableName(${columns.joinToString(", ") { it.sql.trim() }})"
        db.execSQL(sql)
    }

    /**
     * It is ok for this to return error
     */
    fun addColumnIfNotExist(
        tableName: String,
        column: Column
    ) = kotlin.runCatching {
        db.execSQL("ALTER TABLE $tableName ADD COLUMN ${column.sql}")
    }

    fun insertOrReplace(tableName: String, contentValues: ContentValues) = kotlin.runCatching {
        -1L != db.insertWithOnConflict(
            tableName,
            null,
            contentValues,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun deleteWhere(tableName: String, whereClause: String?, whereArgs: Array<out String>?) =
        kotlin.runCatching {
            val rowsDeleted = db.delete(
                tableName,
                whereClause,
                whereArgs
            )
            rowsDeleted
        }


    fun readCursor(tableName: String) = kotlin.runCatching {
        db.query(
            tableName,
            null,
            null,
            null,
            null,
            null,
            null
        )
    }
}

sealed class Table(val name: String, val columns: Array<Column>) {
    object NeuronEventsTable : Table(
        NeuronEventFields.EVENT_TABLE_NAME,
        arrayOf(
            Column.TextPrimaryKey(NeuronEventFields.EVENT_UUID),
            Column.Integer(NeuronEventFields.EVENT_TIMESTAMP, nullable = false, default = 0L),
            Column.Text(NeuronEventFields.EVENT_NAME, nullable = false, isAlterColumn = true), // newly added on 2025-03-25
            Column.Text(NeuronEventFields.EVENT_DATA_JSON, nullable = false)
        )
    )

    companion object {
        /**
         * A [List] of all [Table] supported by [SimpleDbManager]
         * add new [Table] here
         */
        val all = listOf(NeuronEventsTable)
    }
}

/**
 * @param isAlterColumn, any [Column] added after initial release of the table should be true here
 */
sealed class Column(val name: String, val isAlterColumn: Boolean) {
    abstract val sql: String

    class TextPrimaryKey(name: String) : Column(name, false) {
        override val sql = "$name TEXT PRIMARY KEY"
    }

    class Text(name: String, nullable: Boolean = true, isAlterColumn: Boolean = false) :
        Column(name, isAlterColumn) {
        override val sql = "$name TEXT${if (nullable) "" else " NOT NULL"}"
    }

    class Integer(
        name: String,
        nullable: Boolean = true,
        default: Long? = null,
        isAlterColumn: Boolean = false
    ) : Column(name, isAlterColumn) {
        override val sql =
            "$name INTEGER${if (nullable) "" else " NOT NULL"} ${if (default != null) " DEFAULT $default" else ""}"
    }
}