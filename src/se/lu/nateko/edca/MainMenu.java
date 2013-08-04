package se.lu.nateko.edca;

import se.lu.nateko.edca.BackboneSvc.SvcAccessor;
import se.lu.nateko.edca.svc.LocalSQLDBhelper;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

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
 * The main Activity of the Emergency Data Collector for Android (EDCA). It *
 * handles the main menu and makes some initialization calls.				*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.53, 2013-08-01												*
 * 																			*
 ****************************************************************************/
public class MainMenu extends Activity {
	/** The error tag for this Activity. */
	public static final String TAG = "MainMenu";
	/** A constant defining a request code to send to Google Play services. Is returned in onActivityResult(). */
	private static final int GOOGLE_PLAY_SERVICES_REQUEST = 911;
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
    public void onCreate(Bundle savedInstanceState) {
    	Log.d(TAG, "onCreate(Bundle) called.");
    	
        setContentView(R.layout.main); // Display the "main" layout in this Activity.
        
        super.onCreate(savedInstanceState);        
    }
    
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == GOOGLE_PLAY_SERVICES_REQUEST) {
			Log.v(TAG, "onActivityResult(requestCode=" + requestCode + ", resultCode=" + resultCode + ", Intent) called.");
			switch (resultCode) {
				case Activity.RESULT_OK:
					Log.i(TAG, "Google Play services made available by the user.");
					break;
				default: // The Google Play Services are still not available, but the check will be performed again after this in onResume().
					Log.i(TAG, "Google Play services were not made available by the user.");
					AlertDialog alertDialog = new AlertDialog.Builder(this).create();
					alertDialog.setMessage(getString(R.string.app_gplaysvc_required));
					
					/* Add a button to the dialog and set its text and button listener. */
					alertDialog.setButton(getString(R.string.service_alert_buttontext_ok), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							exit();
							dialog.dismiss();
						}
					});		
					alertDialog.show(); // Display the dialog to the user.
					break;
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onResume() {
    	Log.d(TAG, "onResume() called.");
    	
    	connectGooglePlayServices(); // Make sure that Google Play services are accessible.
    	
    	/* Start the BackboneSvc Service so that it has a lower probability of being killed if all Activities unbind and are stopped. */
        startService(new Intent(this, BackboneSvc.class));
    	
    	/* Bind to the BackboneSvc Service. */	        
        bindService(new Intent(MainMenu.this, BackboneSvc.class), mServiceConnection, Context.BIND_AUTO_CREATE);
        
		super.onResume();
	}
    
    /**
	 * Continuation of the create/resume process, called from onServiceConnected
	 * after having bound to the Service.
	 */
	protected void onBound() {
		Log.d(TAG, "onBound() called.");
		mService.setActiveActivity(MainMenu.this);
		findViewById(R.id.main_webconnection).setAnimation(mService.getAnimationNoQueue());
		mService.renewLastSrvConnection(mService.mInitialRenewSrvConnection);
	}
    
    @Override
	protected void onPause() {
    	Log.d(TAG, "onPause() called.");
		unbindService(mServiceConnection);
//		Log.v(TAG, "unbindService(mServiceConnection) finished.");
		super.onPause();
	}

    @Override
    public void onDestroy()
    {
    	Log.d(TAG, "onDestroy() called.");
        super.onDestroy();
    }
    
    /** Called when clicking the map-button. */
    public void onClickMap(View mapButtonView) {
    	Log.d(TAG, "onClickMap(View) called.");
    	Intent intentMap = new Intent(MainMenu.this, MapViewer.class);
        startActivity(intentMap);
    }
    
    /** Called when clicking the layers-button. */
    public void onClickLayers(View layersButtonView) {
    	Log.d(TAG, "onClickLayers(View) called.");
    	Intent intentLayers = new Intent(MainMenu.this, LayerViewer.class);
        startActivity(intentLayers);
    }
    
    /** Called when clicking the servers-button. */
    public void onClickServers(View srvButtonView) {
    	Log.d(TAG, "onClickServers(View) called.");
    	Intent intentServers = new Intent(MainMenu.this, ServerViewer.class);
        startActivity(intentServers);
    }
    
    /** Called when clicking the about-button. */
    public void onClickAbout(View aboutButtonView) {
    	Log.d(TAG, "onClickAbout(View) called.");

    	Intent intentAbout = new Intent(MainMenu.this, About.class);
        startActivity(intentAbout);
    }

    /** Called when clicking the exit-button. */
    public void onClickExit(View exitButtonView) {
    	Log.d(TAG, "onClickExit(View) called.");
    	
    	/* If there is geometry in an active layer, remind the user to save it before proceeding and offer to cancel. */
		if(mService.getActiveLayer() != null) {
			if(mService.getActiveLayer().hasGeometry()) {
				/* Show a dialog warning the user about losing unsaved geometry. */
				new AlertDialog.Builder(mService.getActiveActivity())
		    		.setIcon(android.R.drawable.ic_dialog_alert)
		    		.setTitle(R.string.menu_loseunsavedalert_title)
		    		.setMessage(R.string.menu_loseunsavedalert_msg)
		    		.setPositiveButton(R.string.menu_loseunsavedalert_confirm, new DialogInterface.OnClickListener() {
		    			public void onClick(DialogInterface dialog, int which) {
		    				Log.v(TAG, "PositiveButton.onClick() called. Navigating to home screen.");
		    				exit();
		    				dialog.dismiss();
		    			}
		    		})
		    		.setNegativeButton(R.string.menu_loseunsavedalert_cancel, null)
		    		.show();
			}
			else {// Else, exit.
				exit();
			}
		}
		else // Else, exit.
			exit();
    }
    
	/**
	 * Checks for Google Play services and lets the user
	 * take action to provide them if they are missing
	 * or not up-to-date.
	 */
	private void connectGooglePlayServices() {
		/* Check that Google Play services is available. */
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		Log.d(TAG, "connectGooglePlayServices() called with resultCode=" + resultCode + ".");
		
		if (resultCode == ConnectionResult.SUCCESS) // If Google Play services is available:
			Log.i(TAG, "Google Play services are available.");
        else { // Google Play services were not available for some reason:
            /* Get the error dialog from Google Play services. */
            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, GOOGLE_PLAY_SERVICES_REQUEST);
            if (errorDialog != null) // If Google Play services could provide an error dialog:
            	errorDialog.show();
            else {
            	AlertDialog alertDialog = new AlertDialog.Builder(this).create();
				alertDialog.setMessage(getString(R.string.app_gplaysvc_notavailable));
				
				/* Add a button to the dialog and set its text and button listener. */
				alertDialog.setButton(getString(R.string.service_alert_buttontext_ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						exit();
						dialog.dismiss();    		      
					}
				});		
				alertDialog.show(); // Display the dialog to the user.
            }
        }
	}
	
	/**
	 * Performs all exit tasks. Clears the layers table
	 * from inaccessible layers, stops the BackboneSvc
	 * and navigates the user to the Home Screen.
	 */
	private void exit() {
		Log.d(TAG, "exit() called.");
		mService.getSQLhelper().deleteData(LocalSQLDBhelper.TABLE_LAYER, LocalSQLDBhelper.KEY_LAYER_ID, LocalSQLDBhelper.ALL_RECORDS,
				LocalSQLDBhelper.KEY_LAYER_USEMODE + " % " + LocalSQLDBhelper.LAYER_MODE_STORE + " <> 0"); // Remove all non-stored layers from the table since they can no longer be accessed.
		mService.stopService(new Intent(this, BackboneSvc.class)); // The user wants to exit, no need to keep the BackgroundSvc alive.
		startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
	}
}