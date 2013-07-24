package se.lu.nateko.edca;

import se.lu.nateko.edca.BackboneSvc.SvcAccessor;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

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
 * Activity class showing some basic info about the application, such as	*
 * version number, licenses and how to contact the author.					*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.26, 2013-07-23												*
 ****************************************************************************/
public class About extends Activity {
	/** The error tag for this Activity. */
	private static final String TAG = "About";
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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);
        
        /* Set the version text to display the version name given in the manifest. */
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            TextView versionTextView = (TextView) findViewById(R.id.textView_about_content_value_version);
            versionTextView.setText(version);
        } catch (NameNotFoundException e) { Log.e(TAG, e.getMessage()); }
        
        ((TextView) findViewById(R.id.about_body_license)).setMovementMethod(LinkMovementMethod.getInstance());
    }


	@Override
	protected void onResume() {
    	/* Bind to the BackboneSvc Service. */
		Intent serviceIntent = new Intent(About.this, BackboneSvc.class);		        
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        
		super.onResume();
	}
	
	/**
	 * Continuation of the create/resume process, called from onServiceConnected
	 * after having bound to the Service.
	 */
	protected void onBound() {
		Log.d(TAG, "onBound() called.");
		mService.setActiveActivity(About.this);
		findViewById(R.id.about_webconnection).setAnimation(mService.getAnimationNoQueue());
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause() called.");
		unbindService(mServiceConnection);
//		Log.v(TAG, "unbindService(mServiceConnection) finished.");
		super.onPause();
	}

	/**
	 * When the user clicks the Google Play Services license TextView;
	 * display a dialog showing the license information.
	 */
	public void onClickGooglePlayServicesLicense(View source) {
		Log.d(TAG, "onClickGooglePlayServicesLicense() called.");
		String licenseInfo = GooglePlayServicesUtil.getOpenSourceSoftwareLicenseInfo(this);
		if (licenseInfo != null)
			mService.showAlertDialog(licenseInfo, null);
        else
            mService.showAlertDialog(getString(R.string.about_content_gplaysvc_none), null);		
	}
}
