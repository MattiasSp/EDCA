package se.lu.nateko.edca;

import java.util.Date;

import se.lu.nateko.edca.BackboneSvc.SvcAccessor;
import se.lu.nateko.edca.svc.LocalSQLDBhelper;
import se.lu.nateko.edca.svc.ServerConnection;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TableRow;
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
 * @version 0.66, 2013-08-01												*
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

    /** Whether editing is locked (true) or enabled (false). */
	private Boolean mEditsEnabled;
	/** The row id in the local SQLite database of the currently displayed ServerConnection, or -1 if a new ServerConnection is created. */
    private Long mRowId;
    /** Flag showing whether or not to use simple address mode, only requiring a single combined address. 0 means exploded address mode, 1 means simple mode. */
    private int mAddressMode;
    /** Constant identifying the simple address mode, where the entire server address is entered as a single input String. */
    public static final int SIMPLE_ADDRESS_MODE = 1;
    /** Constant identifying the exploded address mode, where the server address has to be entered as separate parts. */
    public static final int EXPLODED_ADDRESS_MODE = 0;
    /** String array holding previous input to be rewritten into the EditText boxes. */
    private String[] mTempText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	Log.d(TAG, "onCreate(Bundle) called.");
        
        /* Set instance state. Use savedInstanceState if possible, otherwise use
         * Intent extras. If there is neither use the default (-1, new connection). */
		Bundle instanceState = savedInstanceState;
        if(instanceState == null) {
        	instanceState = getIntent().getExtras();
        	if(instanceState == null) { // Use defaults.
        		mRowId = (long) -1;
        		mEditsEnabled = true;
        		mAddressMode = SIMPLE_ADDRESS_MODE;
        	}
        }
        if(instanceState != null) { // Get state from Bundle.
        	mRowId = instanceState.getLong(BackboneSvc.PACKAGE_NAME + ".id", (long) -1);
        	mEditsEnabled = instanceState.getBoolean(BackboneSvc.PACKAGE_NAME + ".editsenabled", true);
        	mAddressMode = instanceState.getInt(BackboneSvc.PACKAGE_NAME + ".addressmode", SIMPLE_ADDRESS_MODE);
        	mTempText = instanceState.getStringArray(BackboneSvc.PACKAGE_NAME + ".text");
        }
        Log.v(TAG, "mRowId: " + String.valueOf(getRowId()) + ", mEditsEnabled: " + String.valueOf(mEditsEnabled)+ ", mAddressMode: " + String.valueOf(mAddressMode));        

        /* Set the layout and store the relevant resource references. */
        setContentView(R.layout.srvedit);

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

	    /* Setup the layout (fields, buttons etc.). */
	    updateLayout(mTempText);
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause() called.");      
		unbindService(mServiceConnection);
		super.onPause();
	}
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BackboneSvc.PACKAGE_NAME + ".id", getRowId());
        outState.putBoolean(BackboneSvc.PACKAGE_NAME + ".editsenabled", mEditsEnabled);
        outState.putInt(BackboneSvc.PACKAGE_NAME + ".addressmode", mAddressMode);
        if ((Long) getRowId() == -1) // If a new ServerConnection was being created;            
        	/* Store the currently entered information in the outState Bundle
        	 * so that it will not be lost in case of a sudden interruption. */
        	outState.putStringArray(BackboneSvc.PACKAGE_NAME + ".temptext", getText()); 
    }

    /**
     * Method that displays the information on the selected connection in
     * the text fields and sets the fields' and buttons' states.
     * @param text Pass in a String array to fill the EditText boxes with previous input.
     */
    public void updateLayout(String[] text) {
    	setAddressMode(mAddressMode); // Hides the fields not required.
    	enableEdits(mEditsEnabled); // Set the fields to their previous (or the default, enabled) state.
    	((TextView) findViewById(R.id.srvedit_textview_title)).setText(
    			(getRowId() == -1) ? R.string.srvedit_title_create : R.string.srvedit_title_edit); // Set heading to "Create..." or "Edit..." depending on whether a new ServerConnection is being created or not.

    	/* Set the text in the EditText boxes. */
    	if(text != null) // Use previous input or intent extras if there is such (else leave fields blank):
	    	setText(text);

    	/* Set the button states. */
    	if(mService.getConnectState() == BackboneSvc.CONNECTING) { // If a server is currently being connected to:
    		if(getRowId() == mService.getConnectingRow()) // If the ServerEditor is viewing the server that is being connected to:
    			setButtonState(BackboneSvc.CONNECTING);
    		else // If the ServerEditor is viewing another server.
    			setButtonState(BackboneSvc.DISCONNECTED);
    	}
    	else if(mService.getActiveServer() == null) // There is no active server to be connected to.
	    	setButtonState(BackboneSvc.DISCONNECTED);
    	else if(getRowId() == mService.getActiveServer().getID()) // The ServerConnection that is the active server is being edited:
			setButtonState(BackboneSvc.CONNECTED);
		else // If there is no connection either present or underway related to this server.
			setButtonState(BackboneSvc.DISCONNECTED);
    }

    /**
     * Listener method which is called when the Simple Address Mode
     * CheckBox is clicked. Hides the non-required fields and shows
     * the ones required for the selected mode.
     * @param view The View object that was clicked.
     */
    public void onClickSimpleAddress(View view) {
    	mAddressMode = (mAddressMode == SIMPLE_ADDRESS_MODE) ? EXPLODED_ADDRESS_MODE : SIMPLE_ADDRESS_MODE;
    	setAddressMode(mAddressMode);
    }
    
    /**
     * Listener method which is called when the Save/Edit button is clicked, it
     * will save the input text to a new ServerConnection or update an existing one
     * in the local SQLite database.
     * @param view The View object that was clicked.
     */
    public void onClickSaveEdit(View view) {
    	Log.d(TAG, "onClickSaveEdit(View) called.");
    	
    	if(mEditsEnabled == false) { // If editing was locked, i.e. the "Edit" button was pressed: enable edits and disconnect if this is the active server.
    		if(mService.getActiveServer() != null) {// There is an active ServerConnection:
    			if(mService.getActiveServer().getID() == getRowId()) { // If the currently active ServerConnection is being edited, disconnect it.
    				Log.i(TAG, "Editing the active server connection. Disconnecting...");
        			mService.clearConnection(false);
    			}
    		}
    		else
    			Log.v(TAG, "No active server.");
    		enableEdits(true); // Either way, enable editing.
    		((ToggleButton) findViewById(R.id.srvedit_button_connect)).setEnabled(false); // Disable the connect button while editing.
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
    						LocalSQLDBhelper.KEY_SRV_SIMPLE,
    						LocalSQLDBhelper.KEY_SRV_IP,
    						LocalSQLDBhelper.KEY_SRV_PORT,
    						LocalSQLDBhelper.KEY_SRV_PATH,
    						LocalSQLDBhelper.KEY_SRV_WORKSPACE,
    						LocalSQLDBhelper.KEY_SRV_MODE
    					}
    					, new String[] { text[0], text[1], text[2], text[3], text[4], text[5], text[6], String.valueOf(mAddressMode) });
        		if (id > 0)
        		{
                    mRowId = id;
                    Log.v(TAG, "ServerConnection stored in the local SQLite database; mRowId: " + String.valueOf(getRowId()));
        		}
        	}
        	/* There is a row ID (other than -1), i.e. this is an edit-connection operation: */
        	else {
        		// Update the server connection row in the local SQLite database.
        		mService.getSQLhelper().updateData(LocalSQLDBhelper.TABLE_SRV, getRowId(), LocalSQLDBhelper.KEY_SRV_ID,
        				new String[] { LocalSQLDBhelper.KEY_SRV_NAME, LocalSQLDBhelper.KEY_SRV_SIMPLE, LocalSQLDBhelper.KEY_SRV_IP, LocalSQLDBhelper.KEY_SRV_PORT, LocalSQLDBhelper.KEY_SRV_PATH, LocalSQLDBhelper.KEY_SRV_WORKSPACE, LocalSQLDBhelper.KEY_SRV_MODE },
        				new String[] { text[1], text[2], text[3], text[4], text[5], text[6], String.valueOf(mAddressMode) });
        		Log.v(TAG, "ServerConnection (rowId: " + String.valueOf(getRowId()) + ") in the local SQLite database was updated.");
        	}
        	enableEdits(false); // Disable edits either way.
        	((ToggleButton) findViewById(R.id.srvedit_button_connect)).setEnabled(true); // Enable the connect button.
    	}
    }
    
    /**
     * Listener method that sets or clears the active server in the BackboneSvc and
     * makes a getCapabilities request to the active ServerConnection on "connecting".
     * @param view The view that initiated the call.
     */
    public void onClickConnect(View view) {
    	if(!((ToggleButton) findViewById(R.id.srvedit_button_connect)).isChecked()) {
    		Log.d(TAG, "onClickConnect(View) called. Deactivating...");
    		mService.clearConnection(false);
    	}
    	else {
    		Log.d(TAG, "onClickConnect(View) called. Activating...");
    		/* Check for a network connection. I there is none, send the user to wireless settings. */
    		NetworkInfo netinfo = ((ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo(); // Get information about the active (?) network.
    		if(netinfo != null) {
    			if(netinfo.isConnected()) {
    				/* Make a GetCapabilities request. */
    	        	String[] text  = getText();
    	        	String currentDate = Utilities.DATE_LONG.format(new Date());    		
    	    		mService.makeGetCapabilitiesRequest(new ServerConnection(getRowId(), currentDate, text[1], text[2], text[3], text[4], text[5], text[6], mAddressMode));
    			}
    			else
    				noNetwork(); // Report that the network is unavailable.    				
    		}
    		else
    			noNetwork(); // Report that the network is unavailable.
    		
    	}
    }
    
    /**
     * Method for enabling or disabling use of the text fields
     * and the simple address CheckBox.
     * @param enable The state to set the text fields and the button to.
     */
    public void enableEdits(boolean enable) {
    	Log.d(TAG, "enableEdits(" + String.valueOf(enable) + ") called.");

    	((CheckBox) findViewById(R.id.srvedit_checkbox_mode)).setEnabled(enable);
        ((EditText) findViewById(R.id.srvedit_edittext_name)).setEnabled(enable);
        ((EditText) findViewById(R.id.srvedit_edittext_simple)).setEnabled(enable);
        ((EditText) findViewById(R.id.srvedit_edittext_ip)).setEnabled(enable);
        ((EditText) findViewById(R.id.srvedit_edittext_port)).setEnabled(enable);
        ((EditText) findViewById(R.id.srvedit_edittext_path)).setEnabled(enable);
        ((EditText) findViewById(R.id.srvedit_edittext_workspace)).setEnabled(enable);
        
        mEditsEnabled = enable; // Update the flag to record the enabled state.
        
        Button saveEditButton = (Button) findViewById(R.id.srvedit_button_saveedit);
        if(enable)
        	saveEditButton.setText(R.string.srvedit_button_save);
        else
        	saveEditButton.setText(R.string.srvedit_button_edit);
    }
    
    /**
	 * Method that changes the state of the Save/Edit and Connect buttons.
	 * Pass in 0 for "Disconnected", 1 for "Connected" and 2 for "Connecting".
	 * @param state The state to set the Connect Button to.
	 */
	public void setButtonState(int state) {
//		Log.d(TAG, "setConnectButtonState(state=" + String.valueOf(state) + ") called.");

		ToggleButton connectButton = (ToggleButton) findViewById(R.id.srvedit_button_connect);
		Button saveEditButton = (Button) findViewById(R.id.srvedit_button_saveedit);
		switch(state) {
			case BackboneSvc.DISCONNECTED:
				connectButton.setChecked(false);
				connectButton.setEnabled((mEditsEnabled || mService.getConnectState() == BackboneSvc.CONNECTING) ? false : true); // Don't enable the connect button while editing or while a connection attempt is being made.
				saveEditButton.setEnabled(true);
				Log.i(TAG, "Connect button set to DISCONNECTED.");
				break;
			case BackboneSvc.CONNECTED:
				connectButton.setChecked(true);
				connectButton.setEnabled((mEditsEnabled) ? false : true); // Don't enable the connect button while editing.
				saveEditButton.setEnabled(true);
				Log.i(TAG, "Connect button set to CONNECTED.");
				break;
			case BackboneSvc.CONNECTING:    		
				connectButton.setChecked(false);
				connectButton.setText(getString(R.string.srvedit_button_connecting));
				connectButton.setEnabled(false);
				saveEditButton.setEnabled(false);
				Log.i(TAG, "Connect button set to CONNECTING.");
				break;
			default:
				Log.e(TAG, "Invalid argument for setting Connect button state.");
				break;
		}
	}
	
	/**
	 * Hides or shows the address fields to show simple
	 * or exploded address input mode.
	 * @param mode Pass 1 to show the simple address mode or 0 to show the exploded address mode.
	 */
	private void setAddressMode(int mode) {
		((CheckBox) findViewById(R.id.srvedit_checkbox_mode)).setChecked((mode == SIMPLE_ADDRESS_MODE));
		if((mode == SIMPLE_ADDRESS_MODE)) {
			((TableRow) findViewById(R.id.srvedit_tablerow_simple_head)).setVisibility(View.VISIBLE);
			((TableRow) findViewById(R.id.srvedit_tablerow_simple_edittext)).setVisibility(View.VISIBLE);
			((TableRow) findViewById(R.id.srvedit_tablerow_ipport_head)).setVisibility(View.GONE);
			((TableRow) findViewById(R.id.srvedit_tablerow_ipport_edittext)).setVisibility(View.GONE);
			((TableRow) findViewById(R.id.srvedit_tablerow_path_head)).setVisibility(View.GONE);
			((TableRow) findViewById(R.id.srvedit_tablerow_path_edittext)).setVisibility(View.GONE);
			((TableRow) findViewById(R.id.srvedit_tablerow_workspace_head)).setVisibility(View.GONE);
			((TableRow) findViewById(R.id.srvedit_tablerow_workspace_edittext)).setVisibility(View.GONE);
		}
		else {
			((TableRow) findViewById(R.id.srvedit_tablerow_simple_head)).setVisibility(View.GONE);
			((TableRow) findViewById(R.id.srvedit_tablerow_simple_edittext)).setVisibility(View.GONE);
			((TableRow) findViewById(R.id.srvedit_tablerow_ipport_head)).setVisibility(View.VISIBLE);
			((TableRow) findViewById(R.id.srvedit_tablerow_ipport_edittext)).setVisibility(View.VISIBLE);
			((TableRow) findViewById(R.id.srvedit_tablerow_path_head)).setVisibility(View.VISIBLE);
			((TableRow) findViewById(R.id.srvedit_tablerow_path_edittext)).setVisibility(View.VISIBLE);
			((TableRow) findViewById(R.id.srvedit_tablerow_workspace_head)).setVisibility(View.VISIBLE);
			((TableRow) findViewById(R.id.srvedit_tablerow_workspace_edittext)).setVisibility(View.VISIBLE);
		}
	}
    
    /**
     * Get method for the text written in the EditText boxes by the user.
     * @return A seven field String array with ServerConnection name, simple address, ip, port, path, workspace and date of last use.
     */
    private String[] getText() {
//    	Log.v(TAG, "mLastUsedText: " + mLastUsedText.getText().toString());
    	String[] text = new String[] {
    			((TextView) findViewById(R.id.srvedit_textview_lastused_content)).getText().toString(),
    			((EditText) findViewById(R.id.srvedit_edittext_name)).getText().toString(),
    			((EditText) findViewById(R.id.srvedit_edittext_simple)).getText().toString(),
    	        ((EditText) findViewById(R.id.srvedit_edittext_ip)).getText().toString(),
    	        ((EditText) findViewById(R.id.srvedit_edittext_port)).getText().toString(),
    	        ((EditText) findViewById(R.id.srvedit_edittext_path)).getText().toString(),
    	        ((EditText) findViewById(R.id.srvedit_edittext_workspace)).getText().toString()
    	};
    	Log.d(TAG, "getText() called: " + text[0] + "; " + text[1] + "; " + text[2] + "; " + text[3] + "; " + text[4] + "; " + text[5] + "; " + text[6]);
    	return text;
    }

    /**
     * Set method for the texts written in the EditText boxes by the user.
     * @param text A seven field String array with ServerConnection name, simple address, ip, port, path, workspace and date of last use.
     */
    private void setText(String[] text) {
//    	Log.v(TAG, "setText(String[] == null: " + String.valueOf(text == null) + ")");
    	if(text != null) {
    		((TextView) findViewById(R.id.srvedit_textview_lastused_content)).setText(text[0]);
    		((EditText) findViewById(R.id.srvedit_edittext_name)).setText(text[1]);
    		((EditText) findViewById(R.id.srvedit_edittext_simple)).setText(text[2]);
    		((EditText) findViewById(R.id.srvedit_edittext_ip)).setText(text[3]);
    		((EditText) findViewById(R.id.srvedit_edittext_port)).setText(text[4]);
    		((EditText) findViewById(R.id.srvedit_edittext_path)).setText(text[5]);
    		((EditText) findViewById(R.id.srvedit_edittext_workspace)).setText(text[6]);
    		Log.d(TAG, "setText() called: " + text[0] + "; " + text[1] + "; " + text[2] + "; " + text[3] + "; " + text[4] + "; " + text[5] + "; " + text[6]);
    	}
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
    	int switcher = 	(text[1].contentEquals("")) ?																1
    			: (mAddressMode == SIMPLE_ADDRESS_MODE && !Utilities.isValidAddress(text[2])) ?						2
    				: (mAddressMode == EXPLODED_ADDRESS_MODE && !Utilities.isIP(text[3])) ?							3
    					: (mAddressMode == EXPLODED_ADDRESS_MODE && Utilities.isInteger(text[4])[0] == 0) ?			4
    						: (mAddressMode == EXPLODED_ADDRESS_MODE && !Utilities.isValidPath(text[5])) ?			5
    							: (mAddressMode == EXPLODED_ADDRESS_MODE && !Utilities.isValidWorkspace(text[6])) ?	6
    								:																				0;
    	
    	/* If a field was filled incorrectly, display a warning and let the user return. */
    	if(switcher != 0) {
    		Log.v(TAG, "Input invalid; alert the user about which field needs editing and the format to use.");
    		switch(switcher) {
    		case 1:
    			mService.showAlertDialog(getString(R.string.srvedit_alert_text_needname), null);
    			break;
    		case 2:
    			mService.showAlertDialog(getString(R.string.srvedit_alert_text_badsimple) + Utilities.URL_PREFIX + ".", null);
    			break;
    		case 3:
    			mService.showAlertDialog(getString(R.string.srvedit_alert_text_badip), null);
    			break;
    		case 4:
    			mService.showAlertDialog(getString(R.string.srvedit_alert_text_badport), null);
    			break;
    		case 5:
    			mService.showAlertDialog(getString(R.string.srvedit_alert_text_badpath), null);
    			break;
    		case 6:
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
    
    /**
     * Alerts the user that a network connection is missing
     * and send the user to the system's Wireless Settings.
     */
    private void noNetwork() {
    	setButtonState(BackboneSvc.DISCONNECTED); // Without a network connection it can't be connected.
    	/* Create an AlertDialog to inform the user, which upon pressing "ok"
    	 * will send the user the the system's Wireless Settings. */
    	AlertDialog alertDialog = new AlertDialog.Builder(this).create();
		alertDialog.setMessage(getString(R.string.srvedit_alert_nonetwork));		
		/* Add a button to the dialog and set its text and button listener. */
		alertDialog.setButton(getString(R.string.service_alert_buttontext_ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); // Send the user to system Wireless Settings to fix the problem.
				dialog.dismiss();    		      
			}
		});		
		alertDialog.show(); // Display the dialog to the user.
    }
}
