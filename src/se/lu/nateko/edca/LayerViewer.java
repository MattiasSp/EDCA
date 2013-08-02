package se.lu.nateko.edca;

import se.lu.nateko.edca.BackboneSvc.SvcAccessor;
import se.lu.nateko.edca.svc.GeoHelper;
import se.lu.nateko.edca.svc.GeographyLayer;
import se.lu.nateko.edca.svc.LocalSQLDBhelper;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

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
 * An Activity class that displays the layers available on the geospatial	*
 * server, connected to with the active ServerConnection. Using a Context	*
 * Menu, the user can specify which layers to display, which one to collect	*
 * data for and which ones to store locally.								*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.58, 2013-08-01												*
 * 																			*
 ****************************************************************************/
public class LayerViewer extends ListActivity {
	/** The error tag for this Activity. */
	public static final String TAG = "LayerViewer";
	
	/** Constant defining the Context Menu position of the "Toggle Display" button. */
	private static final int CONTEXT_DISPLAY_ID = Menu.FIRST;
	/** Constant defining the Context Menu position of the "Toggle Target for Upload" button. */
	private static final int CONTEXT_ACTIVATE_ID = Menu.FIRST + 1;
	/** Constant defining the Context Menu position of the "Toggle Store Locally" button. */
    private static final int CONTEXT_STORE_ID = Menu.FIRST + 2;
    /** Constant defining the brightness of the colors used to show layer status (1-255). */
    private static final int COLOR_LAYER_BRIGHTNESS = 235;
	
    /** A reference to the application's background Service, received through the IBinder passed to the ServiceConnection after the Activity has bound to the Service. */
	private BackboneSvc mService;
	
	/** Defines the callback for Service binding, passed to bindService(). */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
    	/**
    	 * Callback method called when the Activity has bound to BackboneSvc,
    	 * casts the IBinder and gets the BackboneSvc instance. Passes continuation
    	 * of onResume() to onBoundDone() to continue initialization operations.
    	 */
		public void onServiceConnected(ComponentName className, IBinder service) {
//        	Log.d(TAG, "onServiceConnected(ComponentName, IBinder) called.");
            mService = ((SvcAccessor) service).mService;            
            onBound(); // Continue with the creation business.
        }

        public void onServiceDisconnected(ComponentName className) {
        	Log.d(TAG, "onServiceDisconnected(ComponentName) called.");
        }        
    };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate(Bundle) called.");
		
		setContentView(R.layout.layerviewer);
		registerForContextMenu(findViewById(android.R.id.list));
		
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onResume() {
		Log.d(TAG, "onResume() called.");
		/* Bind to the BackboneSvc Service. */
		Intent serviceIntent = new Intent(LayerViewer.this, BackboneSvc.class);		        
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        
		super.onResume();
	}
	
	/**
	 * Continuation of the create/resume process, called from onServiceConnected
	 * after having bound to the Service.
	 */
	protected void onBound() {
		Log.d(TAG, "onBound() called.");
		mService.setActiveActivity(LayerViewer.this);
		findViewById(R.id.layerviewer_webconnection).setAnimation(mService.getAnimationNoQueue());
		
		mService.updateLayoutOnState();
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause() called.");		
		unbindService(mServiceConnection);		
		super.onPause();
	}

	@Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	super.onListItemClick(l, v, position, id);
//    	Log.d(TAG, "onListItemClick(ListView, View, int, long) called.");
    	Log.v(TAG, "The user clicked an item (has no effect).");
        // Do nothing.
    }

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		Log.d(TAG, "onCreateContextMenu(ContextMenu, View, ContextMenuInfo) called.");
        
        menu.add(0, CONTEXT_DISPLAY_ID, Menu.NONE, R.string.layerviewer_contextmenu_display);
        menu.add(0, CONTEXT_ACTIVATE_ID, Menu.NONE, R.string.layerviewer_contextmenu_activate);
        menu.add(0, CONTEXT_STORE_ID, Menu.NONE, R.string.layerviewer_contextmenu_store);
        
		super.onCreateContextMenu(menu, v, menuInfo);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
//		Log.d(TAG, "onContextItemSelected(MenuItem) called.");
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		
    	Log.v(TAG, "Context menu item " + String.valueOf(item.getItemId()) + " selected." + " Item row ID: " + String.valueOf(info.id));
        switch(item.getItemId()) {
        	case CONTEXT_DISPLAY_ID:
                onClickContextDisplay(info.id);
                return true;
        	case CONTEXT_ACTIVATE_ID:
                onClickContextActivate(info.id);
                return true;
        	case CONTEXT_STORE_ID:
        		onClickContextStore(info.id);
        		return true;
        }
		return super.onContextItemSelected(item);
	}
	
	/**
     * onClick callback method called when the Upload button is clicked.
     * @param view The view of the Upload button.
     */
	public void onClickUpload(View view) {
		Log.d(TAG, "onClickUpload(View) called.");
		
		/* Display a toast informing the user if there is no active layer. */
		if(mService.getActiveLayer() == null) {
			mService.showToast(R.string.layerviewer_uploadtoast_noactivelayer);
			return;
		}
		
		/* Display a Toast informing the user if there is no data to upload/store. */
		if(mService.getActiveLayer().getGeometry().size() < 1) {
			mService.showToast(R.string.layerviewer_uploadtoast_nodata);
			return;
		}

		/* Display a Toast informing the user if there are no valid line geometries to upload. */
		if(mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_LINE
				&& mService.getActiveLayer().getPointSequence().size() < 1) {
			mService.showToast(R.string.layerviewer_uploadtoast_nolines);
			return;
		}

		/* Display a Toast informing the user if there are no valid line geometries to upload. */
		if(mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_POLYGON
				&& mService.getActiveLayer().getPointSequence().size() < 1) {
			mService.showToast(R.string.layerviewer_uploadtoast_nopolygons);
			return;
		}

		/*
		 * Initiate an upload of the data in the currently active layer,
		 * which, if successful, clears all geometry and attribute data from both
		 * the active layer and the layer's local storage.
		 */
		mService.setUploading(true);
		setLayout_UploadButton(false, true);
		mService.makeSaveOperation(GeoHelper.RWMODE_UPLOAD);
	}
	
	/**
     * Called from onContextItemSelected, selects the clicked layer
     * for being included in a GetMap request.
     * @param rowId The id of the layer selected from the list.
     */
	public void onClickContextDisplay(Long rowId) {
		Log.d(TAG, "onClickContextDisplay(rowId=" + String.valueOf(rowId) + ") called.");
		
		/* Include or exclude the selected layer in the list of layers to request with the GetMap request. */
		Cursor modeCursor = mService.getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_LAYER, new String[] {LocalSQLDBhelper.KEY_LAYER_ID, LocalSQLDBhelper.KEY_LAYER_USEMODE}, rowId, null, false);
		startManagingCursor(modeCursor);
		if(modeCursor.moveToFirst()) {
			if(modeCursor.getInt(1) % LocalSQLDBhelper.LAYER_MODE_DISPLAY != 0) // If the Layer's mode doesn't include "Display"; add that mode.
				mService.getSQLhelper().updateData(LocalSQLDBhelper.TABLE_LAYER, rowId, LocalSQLDBhelper.KEY_LAYER_ID, new String[] {LocalSQLDBhelper.KEY_LAYER_USEMODE}, new String[] {String.valueOf(modeCursor.getInt(1) * LocalSQLDBhelper.LAYER_MODE_DISPLAY)});
			else // Else remove the "Display" mode. 
				mService.getSQLhelper().updateData(LocalSQLDBhelper.TABLE_LAYER, rowId, LocalSQLDBhelper.KEY_LAYER_ID, new String[] {LocalSQLDBhelper.KEY_LAYER_USEMODE}, new String[] {String.valueOf(modeCursor.getInt(1) / LocalSQLDBhelper.LAYER_MODE_DISPLAY)});
		} else Log.e(TAG, "Layer not found!");
		
		mService.setActiveGetMap(null); // Remove the now outdated GetMap object to force a new GetMap request upon entering the MapViewer.
		
		mService.updateLayoutOnState();
	}
	
	/**
     * Called from onContextItemSelected, activates or deactivates the
     * selected layer. Activation performs a DescribeFeature request to
     * the geospatial server to acquire the information needed.
     * @param rowId The id of the layer selected from the list.
     */
	public void onClickContextActivate(Long rowId) {
		Log.d(TAG, "onClickContextActivate(rowId=" + String.valueOf(rowId) + ") called.");
		
		final long id = rowId;
		
		/* If there is geometry in an active layer, remind the user to save it before proceeding and offer to cancel. */
		if(mService.getActiveLayer() != null) {
			if(mService.getActiveLayer().hasGeometry()) {
				/* Show a dialog warning the user about losing unsaved geometry. */
				new AlertDialog.Builder(mService.getActiveActivity())
		    		.setIcon(android.R.drawable.ic_dialog_alert)
		    		.setTitle(R.string.layerviewer_loseunsavedalert_title)
		    		.setMessage(R.string.layerviewer_loseunsavedalert_msg)
		    		.setPositiveButton(R.string.layerviewer_loseunsavedalert_confirm, new DialogInterface.OnClickListener() {
		    			public void onClick(DialogInterface dialog, int which) {
		    				Log.v(TAG, "PositiveButton.onClick() called. Proceeding with activation.");
		    				activateLayer(id);
		    				dialog.dismiss();
		    			}
		    		})
		    		.setNegativeButton(R.string.layerviewer_loseunsavedalert_cancel, null)
		    		.show();
			}
			else // If there is no geometry in danger of being lost, automatically proceed with activation.
				activateLayer(rowId);
		}
		else // If there is no active layer to lose geometry, automatically proceed with activation.
			activateLayer(rowId);
	}
	
	/**
	 * Toggle activation of the selected layer, but first checks the activation state of
	 * any currently active layer to determine which operation to execute.
	 */
	private void activateLayer(Long rowId) {
		Log.d(TAG, "activateLayer(rowId=" + String.valueOf(rowId) + ") called.");
		Cursor layerCursor = mService.getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_LAYER, LocalSQLDBhelper.KEY_LAYER_COLUMNS, rowId, null, false);
		startManagingCursor(layerCursor);		
		if(layerCursor.moveToFirst()) {
			/* If the selected layer is already active; deactivate. */
			if(layerCursor.getInt(2) % LocalSQLDBhelper.LAYER_MODE_ACTIVE == 0) {
				if(layerCursor.getInt(2) % LocalSQLDBhelper.LAYER_MODE_STORE != 0) // Only remove the field definition table if it is not currently stored.
					mService.getSQLhelper().getSQLiteDB().execSQL("DROP TABLE IF EXISTS " + LocalSQLDBhelper.TABLE_FIELD_PREFIX + Utilities.dropColons(layerCursor.getString(1), Utilities.RETURN_LAST));
				mService.deactivateLayers();
				mService.setActiveLayer(null);
				mService.updateLayoutOnState();
			}
			/* Else if the selected layer is stored on the device; read from the stored data. */
			else if(layerCursor.getInt(2) % LocalSQLDBhelper.LAYER_MODE_STORE == 0) {
				mService.setActiveLayer(mService.generateGeographyLayer(layerCursor.getString(1)));
				mService.makeLoadOperation(rowId);
				setLayout_UploadButton(false, false);
			}
			/* Else perform a DescribeFeatureRequest to generate an active layer from information on the geospatial server. */
			else {
				mService.makeDescribeFeatureTypeRequest(mService.getActiveServer(), layerCursor.getString(1) , rowId);
				setLayout_UploadButton(false, false); // Should not upload data while changing the active layer.
			}
		}
	}
	
	/**
     * Called from onContextItemSelected, stores the active layer
     * on the device's external storage.
     * @param rowId The id of the layer selected from the list.
     */
	public void onClickContextStore(Long rowId) {
		Log.d(TAG, "onClickContextStore(rowId=" + String.valueOf(rowId) + ") called.");
		
		Cursor layerCursor = mService.getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_LAYER, LocalSQLDBhelper.KEY_LAYER_COLUMNS, rowId, null, false);
		startManagingCursor(layerCursor);
		if(layerCursor.moveToFirst()) {
			final long layerid = rowId;
			final String layername = layerCursor.getString(1);
			final int layermode = layerCursor.getInt(2);
			
			/* If the layer is stored locally: */			
			if(layermode % LocalSQLDBhelper.LAYER_MODE_STORE == 0) {
				
				/* Display a selection dialog that gives the option of deleting the layer from the local storage or cancel. */
				new AlertDialog.Builder(mService.getActiveActivity())
	        	.setIcon(android.R.drawable.ic_delete)
	        	.setTitle(R.string.layerviewer_deletestoreddialog_title)
	        	.setMessage(R.string.layerviewer_deletestoreddialog_msg)
	        	.setPositiveButton(R.string.layerviewer_deletestoreddialog_confirm, new DialogInterface.OnClickListener()
	        	{
	        		public void onClick(DialogInterface dialog, int which) {
	        			/* Delete the layer from the device. */
	        			if(layermode % LocalSQLDBhelper.LAYER_MODE_ACTIVE != 0) // Only remove the field definition table it is it not currently active.
	        				mService.getSQLhelper().getSQLiteDB().execSQL("DROP TABLE IF EXISTS " + LocalSQLDBhelper.TABLE_FIELD_PREFIX + Utilities.dropColons(layername, Utilities.RETURN_LAST));
	        			GeoHelper.deleteGeographyLayer(layername);
	        			
	        			/* Update the layer table to reflect that the layer is no longer stored on the device. */
	        			mService.getSQLhelper().updateData(LocalSQLDBhelper.TABLE_LAYER, layerid, LocalSQLDBhelper.KEY_LAYER_ID, new String[]{LocalSQLDBhelper.KEY_LAYER_USEMODE}, new String[]{String.valueOf(layermode / LocalSQLDBhelper.LAYER_MODE_STORE)});
	        			mService.updateLayoutOnState();
	        			
	        			dialog.dismiss();
	        		}
	        	})
	        	.setNegativeButton(R.string.layerviewer_deletestoreddialog_cancel, null)
	        	.show();
			}
			/* Else, store the data of the active layer on the device's external storage. */
			else {
				/* If the selected layer is not active, notify and return. */
				if(layermode % LocalSQLDBhelper.LAYER_MODE_ACTIVE != 0) {
					mService.showAlertDialog(mService.getString(R.string.layerviewer_contextstore_notactive), null);
					return;
				}
				
				mService.makeSaveOperation(GeoHelper.RWMODE_WRITE);
			}
		}
	}

	/**
	 * Display all available layers from the active database connection,
	 * stored in the local SQLite database. It will also show the selected
	 * local layer if any.
	 */
    public void populateLayerList() {
    	Log.d(TAG, "populateLayerList() called.");
    	Cursor layerList = mService.getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_LAYER, new String[] { LocalSQLDBhelper.KEY_LAYER_ID, LocalSQLDBhelper.KEY_LAYER_NAME }, LocalSQLDBhelper.ALL_RECORDS, null, true); // Get all of the Layers from the local SQLite database and create the item list.
        startManagingCursor(layerList);

        String[] from = new String[] { LocalSQLDBhelper.KEY_LAYER_NAME };
        int[] to = new int[] { R.id.layerviewer_layerlist_rowText };
        
        /* Now set an array adapter and set it to display using our row. */
        SimpleCursorAdapter layers = new SimpleCursorAdapter(this, R.layout.layer_listrow, layerList, from, to);
        setListAdapter(layers);
    }
    
    /**
     * A method that iterates through the items in the list and sets its color
     * based on which mode is recorded in the local SQLite database.
     */
    public synchronized void setLayout_ListItems() {
    	Log.d(TAG, "setLayout_ListItems() called.");
    	Cursor modeCursor = mService.getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_LAYER, LocalSQLDBhelper.KEY_LAYER_COLUMNS, LocalSQLDBhelper.ALL_RECORDS, null, true);
		startManagingCursor(modeCursor);
		
		ListView list = getListView();
		Log.v(TAG, "List child count: "+list.getChildCount());
		TextView listText;
		
		if(modeCursor.moveToFirst()) {
			//Log.i(TAG, "Layer cursor has records.");
			for(int i=0; i < list.getChildCount(); i++) {
				LinearLayout itemTopContainer = (LinearLayout) list.getChildAt(i);
				LinearLayout itemSubContainer = (LinearLayout) itemTopContainer.getChildAt(0);
				listText = (TextView) itemSubContainer.getChildAt(2);
				
				if(modeCursor.getInt(2) == LocalSQLDBhelper.LAYER_MODE_INACTIVE) {
					listText.setTextColor(Color.GRAY);
					Log.v(TAG, "List item " + (i+1) + " color set to grey. Mode: " + modeCursor.getInt(2));
				}
				else {
					int red = (modeCursor.getInt(2) % LocalSQLDBhelper.LAYER_MODE_ACTIVE == 0) ? COLOR_LAYER_BRIGHTNESS : 0;
					int green = (modeCursor.getInt(2) % LocalSQLDBhelper.LAYER_MODE_DISPLAY == 0) ? COLOR_LAYER_BRIGHTNESS : 0;
					int blue = (modeCursor.getInt(2) % LocalSQLDBhelper.LAYER_MODE_STORE == 0) ? COLOR_LAYER_BRIGHTNESS : 0;
					listText.setTextColor(Color.rgb(red, green, blue));
					Log.v(TAG, "List item " + (i+1) + " color set to rgb(" + red + "," + green + "," + blue + "). Mode: " + modeCursor.getInt(2));
				}
				
				if(!modeCursor.moveToNext()) // Break the loop when it's no longer possible to find two next rows.
					break;
			}
		}
		else
			Log.w(TAG, "Layer cursor has no records.");
	}
    
    /**
	 * A method which will display the name of the currently active local layer
	 * in red color. Sending null will clear the text, showing in grey color
	 * that there is no active layer.
	 */
	public void setLayout_ActiveLayer(GeographyLayer layer) {
//		Log.d(TAG, "setLayout_ActiveLayer(GeographyLayer) called.");
		TextView activeLayer = (TextView) findViewById(R.id.layerviewer_content_activelayer);
		if(layer != null) {
//			Log.v(TAG, "Show: '" + layer.getName() + "'");
			activeLayer.setText(layer.getName());
			activeLayer.setTextColor(Color.RED);
		} else {
			activeLayer.setText(R.string.layerviewer_content_noactivelayer);
			activeLayer.setTextColor(Color.GRAY);
		}
	}
	
	/**
	 * Enables or disables the Upload button and sets its text.
	 * @param enable Pass true to enable.
	 * @param uploading Pass true to set the text to "Uploading..."
	 */
	public void setLayout_UploadButton(boolean enable, boolean uploading) {
		Log.d(TAG, "setLayout_EnableUploadButton(" + String.valueOf(enable) + ") called.");
		Button upload = (Button) findViewById(R.id.layerviewer_button_upload);
		upload.setEnabled(enable);
		if(!uploading)
			upload.setText(R.string.layerviewer_button_upload);
		else
			upload.setText(R.string.layerviewer_button_uploading);
	}
}

