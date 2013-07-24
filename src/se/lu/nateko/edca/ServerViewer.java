package se.lu.nateko.edca;

import se.lu.nateko.edca.BackboneSvc.SvcAccessor;
import se.lu.nateko.edca.svc.LocalSQLDBhelper;
import se.lu.nateko.edca.svc.ServerConnection;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
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
 * An activity class that lists ServerConnections and, through a Context	*
 * Menu, allows the user to:												*
 * 	- Launch the ServerEditor Activity in order to create new, edit or		*
 * 		connect to ServerConnections.										*
 *	- Renew the connection to the most recently connected ServerConnection.	*
 *	- Delete ServerConnections.												*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.24, 2013-06-25												*
 * 																			*
 ****************************************************************************/
public class ServerViewer extends ListActivity {
	/** The error tag for this Activity. */
	public static final String TAG = "ServerViewer";

	/** Constant defining the Context Menu position of the "Edit" button. */
    private static final int CONTEXT_EDIT_ID = Menu.FIRST;
    /** Constant defining the Context Menu position of the "Delete" button. */
    private static final int CONTEXT_DELETE_ID = Menu.FIRST + 1;
	
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
//			Log.d(TAG, "onServiceConnected(ComponentName, IBinder) called.");
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
        
		setContentView(R.layout.srvstart);
		registerForContextMenu(findViewById(android.R.id.list));
		
		super.onCreate(savedInstanceState);
	}
	
	@Override
    protected void onResume() {
		Log.d(TAG, "onResume() called.");
		/* Bind to the BackboneSvc Service. */
		Intent serviceIntent = new Intent(ServerViewer.this, BackboneSvc.class);		        
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
		
        super.onResume();
    }
	
	/**
	 * Continuation of the create/resume process, called from onServiceConnected
	 * after having bound to the Service.
	 */
	protected void onBound() {
		Log.d(TAG, "onBound() called.");
		mService.setActiveActivity(ServerViewer.this);
		findViewById(R.id.srvstart_webconnection).setAnimation(mService.getAnimationNoQueue());
		
	    populateSrvList(); // Display all stored servers in the local list.
	    mService.updateLayoutOnState(); // Update the layout to show the currently active server connection name in green.
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause() called.");
		
		unbindService(mServiceConnection);
		
		super.onPause();
	}

	/**
	 * A method which will display the name of the currently active ServerConnection in green color.
	 * @param srv The ServerConnection to display the name of.
	 * @return Boolean showing true if successful, otherwise false.
	 */
	public boolean setLayout_ActiveServer(ServerConnection srv) {
		TextView activesrv = (TextView) findViewById(R.id.srvstart_content_activesrv);
		if(srv != null) {
			Log.i(TAG, "Show: '" + srv.getName() + "' " + srv.toString());			
			activesrv.setText(srv.getName());
			activesrv.setTextColor(Color.GREEN);
			return true;
		} else {
			activesrv.setText(R.string.srvstart_content_noactivesrv);
			activesrv.setTextColor(Color.GRAY);
			return false;
		}
	}
	
	/**
	 * Method that will display the names of all ServerConnections stored
	 * in the local SQLite database.
	 */
    private void populateSrvList() {
    	Cursor srvList = mService.getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_SRV, LocalSQLDBhelper.KEY_SRV_COLUMNS, 0, null, false); // Get all of the ServerConnections from the local SQLite database and create the item list.
        startManagingCursor(srvList);

        String[] from = new String[] { LocalSQLDBhelper.KEY_SRV_NAME };
        int[] to = new int[] { R.id.srvstart_srvlist_rowText };
        
        // Now create an array adapter and set it to display using our row.
        SimpleCursorAdapter servers =
            new SimpleCursorAdapter(this, R.layout.srv_listrow, srvList, from, to);        
        setListAdapter(servers);
    }
    
    /**
     * Makes a connection to the last used ServerConnection and retrieves the results of a getCapabilities request.
     * @param view The view of the Renew Connection button.
     */
    public void onClickRenew(View view) {
    	Log.d(TAG, "onClickRenew(View) called.");
    	mService.renewLastSrvConnection(true);
    }
    
    /**
     * onClick callback method called when the New Server button is clicked.
     * @param view The view of the New Server button.
     */
    public void onClickNewServer(View view) {
    	Log.d(TAG, "onClickNewServer(View) called.");
    	Intent i = new Intent(this, ServerEditor.class);
        startActivity(i);
    }
    
    /**
     * Called from onContextItemSelected, starts the ServerEditor Activity.
     * @param id The id of the ServerConnection selected from the list.
     */
    public void onClickContextEdit(long id) {
    	Log.d(TAG, "onClickContextEdit(id=" + String.valueOf(id) + ") called.");
        Intent i = new Intent(this, ServerEditor.class);
        i.putExtra(LocalSQLDBhelper.KEY_SRV_ID, id);    	
        startActivity(i);
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	super.onListItemClick(l, v, position, id);
//    	Log.d(TAG, "onListItemClick(ListView, View, int, long) called.");
    	Log.v(TAG, "The user clicked an item (has no effect).");
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	Log.d(TAG, "onCreateContextMenu(ContextMenu, View, ContextMenuInfo) called.");
        
        menu.add(0, CONTEXT_EDIT_ID, Menu.NONE, R.string.srvstart_contextmenu_edit);
        menu.add(0, CONTEXT_DELETE_ID, Menu.NONE, R.string.srvstart_contextmenu_delete);
        
        super.onCreateContextMenu(menu, v, menuInfo);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    	Log.d(TAG, "Context item selected. ItemId: " + String.valueOf(item.getItemId()) + ".");
        switch(item.getItemId()) {
        	case CONTEXT_EDIT_ID:
                		onClickContextEdit(info.id);
                		return true;
        	case CONTEXT_DELETE_ID:
        		mService.getSQLhelper().deleteData(LocalSQLDBhelper.TABLE_SRV, LocalSQLDBhelper.KEY_SRV_ID, info.id, null);
        		populateSrvList();
        		return true;
        }
        return super.onContextItemSelected(item);
    }
}
