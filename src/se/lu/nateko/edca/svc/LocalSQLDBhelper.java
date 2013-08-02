package se.lu.nateko.edca.svc;

import se.lu.nateko.edca.Utilities;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/********************************COPYRIGHT***********************************
 * This file is part of the Emergency Data Collector for Android™ (EDCA).	*
 * Copyright © 2013 Mattias Spångmyr.										*
 * 																			*
 *********************************LICENSE************************************
 * EDCA is free software: you can redistribute it and/or modify it under	*
 * the terms of the GNU General Public License as published by the Free		*
 * Software Foundation, either version 3 of the License, or (at your		*
 * option) any later version.												*
 *																			*
 * EDCA is distributed in the hope that it will be useful, but WITHOUT ANY	*
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or		*
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License	*
 * for more details.														*
 *																			*
 * You should have received a copy of the GNU General Public License along	*
 * with EDCA. If not, see "http://www.gnu.org/licenses/".					*
 * 																			*
 * The latest source for this software can be accessed at					*
 * "github.org/mattiassp/edca".												*
 * 																			*
 * EDCA also utilizes the JTS Topology Suite, Version 1.8 by Vivid			*
 * Solutions Inc. It is released under the Lesser General Public License	*
 * ("http://www.gnu.org/licenses/") and its source can be accessed at the	*
 * JTS Topology Suite website ("http://www.vividsolutions.com/jts").		*
 * 																			*
 * Android™ is a trademark of Google Inc. The Android source is released	*
 * under the Apache License 2.0												*
 * ("http://www.apache.org/licenses/LICENSE-2.0") and can be accessed at	*
 * "http://source.android.com/".											*
 * 																			*
 * For other enquiries, e-mail to: edca.contact@gmail.com					*
 * 																			*
 ****************************************************************************
 * SQLiteOpenHelper subclass for storing database handling strings and for	*
 * making SQL queries to the local database.								*
 * 																			*
 * For the table in the database storing layers, the column "mode" stores	*
 * how the layer is used, where the mode is the product of the multiples	*
 * associated with all enabled states. The (prime) multiples of their		*
 * available states are:		 											*
 * 1 = Inactive (not used).													*
 * 2 = Display (in a GetMap request).										*
 * 3 = Stored on the external storage.										*
 * 5 = Active (targeted for data collection).
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.50, 2013-08-01												*
 * 																			*
 ****************************************************************************/
public class LocalSQLDBhelper extends SQLiteOpenHelper {
	/** The error tag for this SQLiteOpenHelper. */
	public static final String TAG = "LocalSQLDBhelper";
	
	/** Constant identifying a "fetch all records" mode for reading from the database (using a SELECT statement). */
	public static final int ALL_RECORDS = 0;
	/** Constant identifying a "fetch most recent ServerConnection" mode for reading from the database (using a SELECT statement). What it really does is it returns the first record when the result table is sorted by the second column. */
	public static final int RECENT_RECORD = -1;
	/** Constant (multiple) identifying the "inactive" mode of a stored layer. */
	public static final int LAYER_MODE_INACTIVE = 1;
	/** Constant (multiple) identifying the "display" mode of a stored layer. */
	public static final int LAYER_MODE_DISPLAY = 2;
	/** Constant (multiple) identifying the "stored on external storage" mode of a stored layer. */
	public static final int LAYER_MODE_STORE = 3;
	/** Constant (multiple) identifying the "active" mode (targeted for data collection) of a stored layer. */
	public static final int LAYER_MODE_ACTIVE = 5;
	
	/** Reference to the local SQLite database specific for this application. */
	private static SQLiteDatabase mDB;
	
	/** Constant defining the database name. */
	public static final String DATABASE_NAME = "localSQLdb";
	/** Constant defining the database version. */
	public static final int DATABASE_VERSION = 2;
	
	/** Constant defining the name of the ServerConnection table in the database. */
	public static final String TABLE_SRV = "servers";
	/** Constant defining the name of the "id" field of the ServerConnection table in the database. */
    public static final String KEY_SRV_ID = "_id";
    /** Constant defining the name of the "last use" field of the ServerConnection table in the database. */
    public static final String KEY_SRV_LASTUSE = "srvlastuse";
    /** Constant defining the name of the "name" field of the ServerConnection table in the database. */
    public static final String KEY_SRV_NAME = "srvname";
    /** Constant defining the name of the "simple" field of the ServerConnection table in the database. */
    public static final String KEY_SRV_SIMPLE = "srvsimple";
    /** Constant defining the name of the "ip" field of the ServerConnection table in the database. */
    public static final String KEY_SRV_IP = "srvip";
    /** Constant defining the name of the "port" field of the ServerConnection table in the database. */
    public static final String KEY_SRV_PORT = "srvport";
    /** Constant defining the name of the "path" field of the ServerConnection table in the database. */
    public static final String KEY_SRV_PATH = "srvpath";
    /** Constant defining the name of the "workspace" field of the ServerConnection table in the database. */
    public static final String KEY_SRV_WORKSPACE = "srvworkspace";
    /** Constant defining the name of the "mode" field of the ServerConnection table in the database. */
    public static final String KEY_SRV_MODE = "srvmode";
    /** Constant combining the names of all fields of the ServerConnection table in the database. */
    public static final String[] KEY_SRV_COLUMNS = new String[] {KEY_SRV_ID, KEY_SRV_LASTUSE, KEY_SRV_NAME, KEY_SRV_SIMPLE, KEY_SRV_IP, KEY_SRV_PORT, KEY_SRV_PATH, KEY_SRV_WORKSPACE, KEY_SRV_MODE};
    
    /** Constant defining the name of the table storing available layers. */
    public static final String TABLE_LAYER = "layers";
    /** Constant defining the name of the "id" field in the layer table. */
    public static final String KEY_LAYER_ID = "_id";
    /** Constant defining the name of the "name" field in the layer table. */
    public static final String KEY_LAYER_NAME = "layername";
    /** Constant defining the name of the "mode" field in the layer table. */
    public static final String KEY_LAYER_USEMODE = "mode";
//	public static final String KEY_LAYER_SRS = "srs"; No support for SRS yet.
    /** Constant combining the names of all fields in the layer table. */
    public static final String[] KEY_LAYER_COLUMNS = new String[] {KEY_LAYER_ID, KEY_LAYER_NAME, KEY_LAYER_USEMODE};
    
    /** Constant defining the name prefix of the Attribute Fields tables in the local SQLite database. */
    public static final String TABLE_FIELD_PREFIX = "fields_";
    /** Constant defining the name of the "id" field in an Attribute Fields table. */
    public static final String KEY_FIELD_ID = "_id";
    /** Constant defining the name of the "name" field in an Attribute Fields table. */
    public static final String KEY_FIELD_NAME = "name";
    /** Constant defining the name of the "nullable" field in an Attribute Fields table. */
    public static final String KEY_FIELD_NULLABLE = "nullable";
    /** Constant defining the name of the "datatype" field in an Attribute Fields table. */
    public static final String KEY_FIELD_DATATYPE = "datatype";
    /** Constant combining the names of all fields in an Attribute Fields table. */
    public static final String[] KEY_FIELD_COLUMNS = new String[] {KEY_FIELD_ID, KEY_FIELD_NAME, KEY_FIELD_NULLABLE, KEY_FIELD_DATATYPE};
	
    /** Server Table creation SQL statement. */
	private static final String CREATE_TABLE_SRV =
        "create table " + TABLE_SRV + " (" + KEY_SRV_ID + " integer primary key autoincrement, "
        + KEY_SRV_NAME + " text not null, " + KEY_SRV_SIMPLE + " text, " + KEY_SRV_IP + " text, " + KEY_SRV_PORT + " text, "
        + KEY_SRV_PATH + " text, " + KEY_SRV_WORKSPACE + " text, " + KEY_SRV_MODE + " integer not null, " + KEY_SRV_LASTUSE + " date);";
	
	/** Layers Table creation SQL statement. */
	private static final String CREATE_TABLE_LAYER =
        "create table " + TABLE_LAYER + " (" + KEY_LAYER_ID + " integer primary key autoincrement, "
        + KEY_LAYER_NAME + " text not null, " + KEY_LAYER_USEMODE + " integer not null);";

	/**
	 * LocalSQLDBhelper constructor taking a Context as a parameter.
	 * @param context Context within which the local SQLite database will operate.
	 */
	public LocalSQLDBhelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Log.d(TAG, "LocalSQLDBhelper(Context) called.");
    }
	
    /**
     * Open the local SQLite database. If it cannot be opened, try to create a new
     * instance of the database. If it cannot be created, throw an exception to
     * signal the failure.
     * 
     * @return this Self reference, allowing this to be chained in an initialization call.
     * @throws SQLException Thrown if the database could be neither opened nor created.
     */
    public LocalSQLDBhelper open() throws SQLException {
        mDB = getWritableDatabase();
        return this;
    }
	
    @Override
    public void onCreate(SQLiteDatabase db) {
    	Log.d(TAG, "onCreate(SQLiteDatabase) called.");
        db.execSQL(CREATE_TABLE_SRV);
        db.execSQL(CREATE_TABLE_LAYER);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                + newVersion + ", which will destroy all old data.");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SRV);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LAYER);
        onCreate(db);
    }
    
    /**
     * Fetches records from the local SQL database.
     * A 0 as argument returns all records, -1 returns the most recently used server connection
     * and other positive integers return the record with the corresponding ID.
     * @param table A String with the table name in the local SQLite database.
     * @param columns A String array with the columns to retrieve. Should include the ID as the first column.
     * @param record An Integer argument specifying which record(s) to return.
     * @param extraCondition If an additional condition is required, send it here.
     * @param noOrder Pass true to avoid ordering the records, i.e. to keep the table order, else false.
     * @return Cursor pointing to the row(s) in the table to be retrieved, or null if nothing was found.
     * @throws SQLException If no record(s) could be found/retrieved.
     */
    public Cursor fetchData(String table, String[] columns, long record, String extraCondition, boolean noOrder) throws SQLException {
    	if(mDB == null) // If there is no stored SQLiteDatabase.
    		return null;
    	else if(record == ALL_RECORDS) {// If the argument is 0, return all records. Sort by second column, then third if present. If there is only one column, sort by that.
    		String sortingString = (noOrder) ? null :
    				(columns.length < 2) ? columns[0] + " DESC" :
    					(columns.length > 2) ? columns[1] + " DESC, " + columns[2] + " ASC" :
    						columns[1] + " DESC";
    		return mDB.query(true, table, columns, extraCondition, null, null, null, sortingString, null);
    	}
    	else if(record == RECENT_RECORD) // Else, if the argument is -1, return the first record when sorted by the second column, i.e. the last used server.
    		return mDB.query(true, table, columns, extraCondition, null, null, null, columns[1] + " DESC", "1");
    	else {// Else, return the record with the specified ID.
    		String conditionString = columns[0] + " = " + String.valueOf(record);
    		if(extraCondition != null)
    			conditionString = conditionString + " AND " + extraCondition;
    		return mDB.query(true, table, columns, conditionString, null, null, null, null, null);
    	}
    }

    /**
     * Insert a new row into the specified table using the column names and values provided.
     * If the note is successfully created return the new rowId for that note, otherwise return
     * a -1 to indicate failure.
     * 
     * @param table The name of the table to add data to.
     * @param column Array of column names specifying which columns to add data to.
     * @param value The value to insert in the corresponding columns given by 'column'.
     * @return The row id or -1 if the insert failed.
     */
    public long insertData(String table, String[] column, String[] value) {
        ContentValues initialValues = new ContentValues();
        if(column.length != value.length)
        	return -1;
        else {
        	for (int i=0; i<column.length; i++)
        		initialValues.put(column[i], value[i]);
        	long row = mDB.insert(table, null, initialValues);
        	Log.i(TAG, "New row (ID: " + String.valueOf(row) + ") inserted in table '" + table + "'.");
        	return row;
        }
    }
    
    /**
     * Update data in the local SQLite database using the details provided.
     * The row to be updated is specified using the rowId, and it is altered to
     * use the String values passed in.
     * 
     * @param table The name of the table to update.
     * @param rowId ID of the row to update.
     * @param key_id The name of the ID column in the table.
     * @param columns String array with the names of the columns to change.
     * @param values String array with the values to set for the columns.
     * @return true If the note was successfully updated, otherwise false.
     */
    public boolean updateData(String table, long rowId, String key_id, String[] columns, String[] values) {
        ContentValues args = new ContentValues();
        
        for(int i=0; i<columns.length; i++) {
        	try {
        		if(values[i] != null)
        			args.put(columns[i], values[i]);
        	} catch (NullPointerException e) { Log.e(TAG, e.getMessage()); }
        }             	
        Log.i(TAG, "Row " + String.valueOf(rowId) + " updated in table '" + table + "'");
        return mDB.update(table, args, key_id + "=" + rowId, null) > 0;
    }
    
    /**
     * Delete a specified (or all) rows in a given table.
     * @param table The table to delete from.
     * @param idField The name of the ID field.
     * @param rowId The ID of the row to delete. 0 means delete all.
     * @param extraCondition An extra condition specifying which rows to delete.
     * @return True if deleted successfully, otherwise false.
     */
    public boolean deleteData(String table, String idField, long rowId, String extraCondition) {
    	if(rowId == ALL_RECORDS && extraCondition == null) {
    		int result = mDB.delete(table, null, null);
    		Log.i(TAG, "All rows (" + result + ") deleted from table '" + table + "'.");
            return result > 0;
    	}
    	else if(rowId == ALL_RECORDS) {
    		int result = mDB.delete(table, extraCondition, null);
    		Log.i(TAG, "All rows mathing extra condition (" + result + ") deleted from table '" + table + "'.");
            return result > 0;
    	}
    	else {
    		Log.i(TAG, "Row " + String.valueOf(rowId) + " deleted from table '" + table + "'");
            return mDB.delete(table, idField + "=" + rowId, null) > 0;
    	}
    }
    
    /**
     * Create a table to hold the field information of a layer.
     * @param layerName The name of the layer whose fields are specified in the resulting table.
     */
    public void createFieldTable(String layerName) {
    	/* Field Table creation SQL statement. */
    	String create_table_field =
            "create table " + TABLE_FIELD_PREFIX + Utilities.dropColons(layerName, Utilities.RETURN_LAST) + " (" + KEY_FIELD_ID + " integer primary key autoincrement, "
            + KEY_FIELD_NAME + " text not null, " + KEY_FIELD_NULLABLE + " boolean not null, "
            + KEY_FIELD_DATATYPE + " text not null);";
    	  	
    	mDB.execSQL(create_table_field);
    }
    
    /**
     * Fetches the local SQLiteDatabase in order to perform e.g. exec commands.
     * @return The database managed by this helper.
     */
    public SQLiteDatabase getSQLiteDB() {
    	return mDB;
    }
    
}
