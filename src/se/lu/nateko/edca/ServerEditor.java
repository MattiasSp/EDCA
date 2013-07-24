package se.lu.nateko.edca;

import java.util.Date;

import se.lu.nateko.edca.BackboneSvc.SvcAccessor;
import se.lu.nateko.edca.svc.LocalSQLDBhelper;
import se.lu.nateko.edca.svc.ServerConnection;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

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
 * Activity class for creating new or editing already stored server			*
 * connections. Can also activate the currently displayed connection.		*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.54, 2013-07-18												*
 * 																			*
 ****************************************************************************/
public class ServerEditor extends Activity {
	/** The error tag for this Activity. */
	public static final String TAG = "ServerEditor";
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
	
    /** EditText view where the user can input the ServerConnection name. */
    private EditText mNameText;
    /** EditText view where the user can input the ServerConnection IP. */
    private EditText mIPText;
    /** EditText view where the user can input the ServerConnection Port. */
    private EditText mPortText;
    /** EditText view where the user can input the ServerConnection path, e.g. "/geoserver". */
    private EditText mPathText;
    /** EditText view where the user can input the ServerConnection workspace name on the geospatial server. */
    private EditText mWorkspaceText;
    /** TextView displaying the time and date when the ServerConnection was last connected to. */
    private TextView mLastUsedText;
    /** Button letting the user save edits or enable editing of the ServerConnection. */
    private Button mSaveEditButton;
    /** ToggleButton letting the user connect to or disconnect from the ServerConnection. */
    private ToggleButton mConnectButton;
    /** Whether editing is locked (true) or enabled (false). */
	private Boolean mEditsLocked;
	/** The row id in the local SQLite database of the currently displayed ServerConnection, or -1 if a new ServerConnection is created. */
    private Long mRowId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Log.d(TAG, "onCreate(Bundle) called.");
        
        /* Set the layout and store the relevant resource references. */
        setContentView(R.layout.srvedit);
        fetchLayoutObjects();
        
        /* Get the stuff from the intent. */
        Intent intent = getIntent();
		mRowId = (Long) intent.getLongExtra(LocalSQLDBhelper.KEY_SRV_ID, -1);
		Log.v(TAG, "mRowId: " + String.valueOf(getRowId()));
		
		super.onCreate(savedInstanceState);
    }
    
    @Override
    protected void onResume() {    	
		/* Bind to the BackboneSvc Service. */
		Intent serviceIntent = new Intent(ServerEditor.this, BackboneSvc.class);		        
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
		
        super.onResume();
    }
    
	/**
	 * Continuation of the create/resume process, called from onServiceConnected
	 * after having bound to the Service.
	 */
	protected void onBound() {
		Log.d(TAG, "onBound() called.");		
	    mService.setActiveActivity(ServerEditor.this);
	    findViewById(R.id.srvedit_webconnection).setAnimation(mService.getAnimationNoQueue());
	    mService.updateLayoutOnState();
	    if(getRowId() == -1) {// If mRowId is -1 (if a new connection is to be created): enable the text fields and set mEditsLocked to false.
	    	((TextView) findViewById(R.id.srvedit_textview_title)).setText(R.string.srvedit_title_create); // Set heading to "Create..."
	    	enableEdits(true);
	    	enableConnectButton(false);
	    }
	    else if(mService.getConnectState() == BackboneSvc.CONNECTING && getRowId() == mService.getConnectingRow()) {// If the ServerEditor is currently viewing the database being connected to; disable the connect button.
	    	enableEdits(false);
	    	enableConnectButton(false);
	    }
	    else
	    	enableEdits(false);
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause() called.");
		
        saveState();        
		unbindService(mServiceConnection);
		
		super.onPause();
	}

    /**
     * Method that displays the information on the selected connection in
     * the text fields and sets the fields uneditable. The fields will be
     * editable again when the edit button is pressed.
     */
    public void populateFields() {
        if (getRowId() != -1) {
            Cursor server = mService.getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_SRV, LocalSQLDBhelper.KEY_SRV_COLUMNS, getRowId(), null, false);
            startManagingCursor(server);
            server.moveToFirst();
            
            String lastuse = server.getString(server.getColumnIndexOrThrow(LocalSQLDBhelper.KEY_SRV_LASTUSE));
            lastuse = (Utilities.isValidDate(lastuse, Utilities.DATE_LONG)) ? lastuse : getString(R.string.srvedit_content_nolastuse);
            
            mLastUsedText.setText(lastuse);
            mNameText.setText(server.getString(
            		server.getColumnIndexOrThrow(LocalSQLDBhelper.KEY_SRV_NAME)));
            mIPText.setText(server.getString(
            		server.getColumnIndexOrThrow(LocalSQLDBhelper.KEY_SRV_IP)));
            mPortText.setText(server.getString(
            		server.getColumnIndexOrThrow(LocalSQLDBhelper.KEY_SRV_PORT)));
            mPathText.setText(server.getString(
            		server.getColumnIndexOrThrow(LocalSQLDBhelper.KEY_SRV_PATH)));
            mWorkspaceText.setText(server.getString(
            		server.getColumnIndexOrThrow(LocalSQLDBhelper.KEY_SRV_WORKSPACE)));
            
        }
        else { // If there is no selected Server Connection, i.e. a new connection is being created, try to fill the EditText boxes with previously entered text.
        	try {
        		mLastUsedText.setText(mService.mTempText[0]);
        		mNameText.setText(mService.mTempText[1]);
                mIPText.setText(mService.mTempText[2]);
                mPortText.setText(mService.mTempText[3]);
                mPathText.setText(mService.mTempText[4]);
                mWorkspaceText.setText(mService.mTempText[5]);
        	} catch (NullPointerException e) { Log.v(TAG, "No text stored."); }
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveState();
        outState.putSerializable(LocalSQLDBhelper.KEY_SRV_ID, getRowId());
    }

    /**
     * Method called in onPause() or onSaveInstanceState(Bundle) that stores the currently
     * entered information in a variable on the BackboneSvc so that it will not be lost in case
     * of a sudden interruption.
     */
    private void saveState() {
        if ((Long) getRowId() == -1) {
        	mService.mTempText = getText();
        }
    }
    
    /**
     * Listener method which is called when the Save/Edit button is clicked, it
     * will save the input text to a new ServerConnection or update an existing one
     * in the local SQLite database.
     * @param view The View object that was clicked.
     */
    public void onClickSaveEdit(View view) {
    	Log.d(TAG, "onClickSaveEdit(View) called.");
    	
    	if(mEditsLocked == true) {// If editing is locked, i.e. the "Edit" button was pressed: enable edits and disconnect if this is the active server.
    		try { // If the currently active ServerConnection is selected, change the Connect button to DISCONNECTED.
    			if(mService.getActiveServer().getID() == getRowId())
    				Log.i(TAG, "Editing the active server connection. Disconnecting...");
        			mService.setActiveServer(null);
        			setConnectButtonState(BackboneSvc.DISCONNECTED);
        			mService.setConnectingRow((long) 0);
        			mService.setConnectState(BackboneSvc.DISCONNECTED);
    		} catch (NullPointerException e) { Log.v(TAG, e.toString() + ": No active server."); }
    		enableEdits(true);
    		enableConnectButton(false);
    	}
    	else // If editing was enabled, i.e. the "Save" button was pressed:
    	{
    		String[] text = getText(); // Fetch the text from the EditText fields.
    		if(!checkText(text)) // If the inputs are not valid, do nothing (return).
    			return;
        	
        	/* If the row ID is -1, i.e. this is a create-new-connection operation: */
        	if (getRowId() == -1) {
        		long id = mService.getSQLhelper().insertData(LocalSQLDBhelper.TABLE_SRV, new String[]
    					{
        					LocalSQLDBhelper.KEY_SRV_LASTUSE,
    						LocalSQLDBhelper.KEY_SRV_NAME,
    						LocalSQLDBhelper.KEY_SRV_IP,
    						LocalSQLDBhelper.KEY_SRV_PORT,
    						LocalSQLDBhelper.KEY_SRV_PATH,
    						LocalSQLDBhelper.KEY_SRV_WORKSPACE
    					}
    					, new String[] { text[0], text[1], text[2], text[3], text[4], text[5] });
        		if (id > 0)
        		{
                    mRowId = id;
                    mService.mTempText = null;
                    Log.v(TAG, "ServerConnection stored in the local SQLite database; mRowId: " + String.valueOf(getRowId()));
        		}
        	}
        	/* There is a row ID (other than -1), i.e. this is an edit-connection operation: */
        	else {
        		// Update the server connection row in the local SQLite database.
        		mService.getSQLhelper().updateData(LocalSQLDBhelper.TABLE_SRV, getRowId(), LocalSQLDBhelper.KEY_SRV_ID,
        				new String[] { LocalSQLDBhelper.KEY_SRV_NAME, LocalSQLDBhelper.KEY_SRV_IP, LocalSQLDBhelper.KEY_SRV_PORT, LocalSQLDBhelper.KEY_SRV_PATH, LocalSQLDBhelper.KEY_SRV_WORKSPACE },
        				new String[] { text[1], text[2], text[3], text[4], text[5] });
        		Log.v(TAG, "ServerConnection (rowId: " + String.valueOf(getRowId()) + ") in the local SQLite database was updated.");
        	}
        	enableConnectButton(true);
        	enableEdits(false);
    	}
    }
    
    /**
     * Listener method that sets or clears the active server in the BackboneSvc and
     * makes a getCapabilities request to the active ServerConnection on "connecting".
     * @param view The view that initiated the call.
     */
    public void onClickConnect(View view) {
    	if(!mConnectButton.isChecked()) {
    		Log.d(TAG, "onClickConnect(View) called. Deactivating...");
    		mService.clearConnection(false);
    	}
    	else {
    		Log.d(TAG, "onClickConnect(View) called. Activating...");
    		/* Make a GetCapabilities request. */
        	String[] text  = getText();
        	String currentDate = Utilities.DATE_LONG.format(new Date());    		
    		mService.makeGetCapabilitiesRequest(new ServerConnection(getRowId(), currentDate, text[1], text[2], text[3], text[4], text[5]));
    	}
    }
    
    /**
     * Method for enabling or disabling use of the text fields.
     * @param enable The state to set the text fields and the button to.
     */
    public void enableEdits(boolean enable) {
    	Log.d(TAG, "enableEdits(" + String.valueOf(enable) + ") called.");

        mNameText.setEnabled(enable);
        mIPText.setEnabled(enable);
        mPortText.setEnabled(enable);
        mPathText.setEnabled(enable);
        mWorkspaceText.setEnabled(enable);
        
        mEditsLocked = !enable;
        
        if(enable)
        	mSaveEditButton.setText(R.string.srvedit_button_save);
        else
        	mSaveEditButton.setText(R.string.srvedit_button_edit);
    }
    
    /**
     * Enables or disables the connect button according to the parameter.
     * @param enable True to enable the connect button.
     */
    public void enableConnectButton(boolean enable) {
    	Log.d(TAG, "enableConnectButton(" + String.valueOf(enable) + ") called.");
    	mConnectButton.setEnabled(enable);
    }
    
    /**
	 * Method that changes the state of the connect button.
	 * Pass in 0 for "Disconnected", 1 for "Connected" and 2 for "Connecting".
	 * @param state The state to set the button to.
	 */
	public void setConnectButtonState(int state) {
//		Log.d(TAG, "setConnectButtonState(state=" + String.valueOf(state) + ") called.");
		switch(state) {
			case BackboneSvc.DISCONNECTED:
				mConnectButton.setChecked(false);
				enableConnectButton(true);
				mSaveEditButton.setEnabled(true);
				Log.i(TAG, "Connect button set to DISCONNECTED.");
				break;
			case BackboneSvc.CONNECTED:
				mConnectButton.setChecked(true);
				enableConnectButton(true);
				mSaveEditButton.setEnabled(true);
				Log.i(TAG, "Connect button set to CONNECTED.");
				break;
			case BackboneSvc.CONNECTING:    		
				mConnectButton.setChecked(false);
				mConnectButton.setText(getString(R.string.srvedit_button_connecting));
				enableConnectButton(false);
				mSaveEditButton.setEnabled(false);
				Log.i(TAG, "Connect button set to CONNECTING.");
				break;
			default:
				Log.e(TAG, "Invalid argument for setting Connect button state.");
				break;
		}
	}

	/**
     * Method for finding the layout objects in the resources and
     * storing their references as instance variables.
     */
    private void fetchLayoutObjects() {
//		Log.d(TAG, "fetchLayoutObjects() called.");
        mSaveEditButton = (Button) findViewById(R.id.srvedit_button_saveedit);
        mConnectButton = (ToggleButton) findViewById(R.id.srvedit_button_connect);
        mNameText = (EditText) findViewById(R.id.srvedit_edittext_name);
        mIPText = (EditText) findViewById(R.id.srvedit_edittext_ip);
        mPortText = (EditText) findViewById(R.id.srvedit_edittext_port);
        mPathText = (EditText) findViewById(R.id.srvedit_edittext_path);
        mWorkspaceText = (EditText) findViewById(R.id.srvedit_edittext_workspace);
        mLastUsedText = (TextView) findViewById(R.id.srvedit_textview_lastused_content);
    }
    
    /**
     * Get method for the text written in the EditText boxes by the user.
     * @return A three field String array with ServerConnection name, ip, port, path, workspace and date of last use.
     */
    private String[] getText() {
//    	Log.v(TAG, "mLastUsedText: " + mLastUsedText.getText().toString());
    	String[] text = new String[] {
    			mLastUsedText.getText().toString(),
    			mNameText.getText().toString(),
    	        mIPText.getText().toString(),
    	        mPortText.getText().toString(),
    	        mPathText.getText().toString(),
    	        mWorkspaceText.getText().toString()
    	};
    	Log.d(TAG, "getText() called: " + text[0] + "; " + text[1] + "; " + text[2] + "; " + text[3] + "; " + text[4] + "; " + text[5]);
    	return text;
    }
    
    /**
     * Checks the return String array from getText() if they are input correctly,
     * and otherwise displays alert dialogs to notify the user.
     * 
     * @param text String array returned from the local getText() method.
     * @return	True if the inputs are valid.
     * @see #getText() The method whose return should be given as input.
     */
    private boolean checkText(String[] text) {
		/*
    	 * Check that all inputs are given correctly. Assign the switcher variable
    	 * a number corresponding to the field that is incorrect, or 0 if they are
    	 * all correctly input.
    	 */
    	int switcher = 	(text[1].contentEquals("")) ?											1
    					: (!Utilities.isIP(text[2])) ?										2
    							: (Utilities.isInteger(text[3])[0] == 0) ?					3
    									: (!Utilities.isValidPath(text[4]))	?				4
    											: (!Utilities.isValidWorkspace(text[5])) ?	5
    													:										0;
    	
    	/* If a field was filled incorrectly, display a warning and let the user return. */
    	if(switcher != 0) {
    		Log.v(TAG, "Input invalid; alert the user about which field needs editing and the format to use.");
    		switch(switcher) {
    		case 1:
    			mService.showAlertDialog(getString(R.string.srvedit_alert_text_needname), null);
    			break;
    		case 2:
    			mService.showAlertDialog(getString(R.string.srvedit_alert_text_badip), null);
    			break;
    		case 3:
    			mService.showAlertDialog(getString(R.string.srvedit_alert_text_badport), null);
    			break;
    		case 4:
    			mService.showAlertDialog(getString(R.string.srvedit_alert_text_badpath), null);
    			break;
    		case 5:
    			mService.showAlertDialog(getString(R.string.srvedit_alert_text_badworkspace), null);
    			break;
    		default:
    			break;
    		}
    		return false;
    	}
    	else {
    		Log.v(TAG, "Input ok.");
    		return true;    	
    	}
    }
    
    /**
     * Get method for the RowId of the ServerConnection that is
     * currently viewed by this ServerEditor.
     * @return The row ID of the ServerConnection being edited.
     */
    public long getRowId() {
    	return mRowId;
    }
}
