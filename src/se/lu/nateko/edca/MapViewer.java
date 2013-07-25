package se.lu.nateko.edca;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import se.lu.nateko.edca.BackboneSvc.SvcAccessor;
import se.lu.nateko.edca.svc.GeographyLayer;
import se.lu.nateko.edca.svc.GeographyLayer.LayerField;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.vividsolutions.jts.geom.Coordinate;

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
 * Activity which displays a Google Maps background with a map image made	*
 * from WMS (GetMap) requests overlayed. It lets the user navigate			*
 * (panning, zooming etc.) as well as add features with attribute data to	*
 * the active local layer.													*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.55, 2013-07-25												*
 * 																			*
 ****************************************************************************/
public class MapViewer extends Activity implements OnCameraChangeListener, OnMapLongClickListener, OnMarkerClickListener, ConnectionCallbacks, OnConnectionFailedListener, LocationListener {
	/** The error tag for this Activity. */
	public static final String TAG = "MapViewer";

	/** Constant identifying the resolution request of a failed LocationClient connection attempt. */
	private static final int LOCATIONCLIENT_CONNECTION_REQUEST = 911;
	/** Constant defining the Location Updates settings. Will update no faster than every 0.2 seconds, to ensure that the last known location doesn't change after the user selects it. */
	private static final LocationRequest LOCATION_REQUEST = LocationRequest.create()
	      .setInterval(5000)
	      .setFastestInterval(200)
	      .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
	/** The time to wait (ms) before stopping location updates if the user has not accepted any location. */
	private static final int LOCATION_REQUEST_TIMEOUT = 1000 * 60;
	/** A timer wating a specified time, after starting to request Location Updates, to stop the updates. */
	private Timer mLocateTimer;
	
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

    /** The MapView used to display the Google Maps background. */
	private MapView mMapView;
	/** The Map object, providing access to the Google Maps API. Used to set up and interact with the map displayed by the MapView. */
	private GoogleMap mMap;
	/** The user's last known location. */
	private Location mLocation;
	/** The LocationCLient used to control and receive Location Updates. */
	private LocationClient mLocationClient;
	/** Flag indicating whether or not Location Updates are being requested. Signals whether or not to start requesting again after recreating the Activity. */
	private boolean mLocating = false;
	/** The TextView which displays location updates and responds to user clicks, letting the user save the location. */
	private TextView mLocationMessageView;
	/** The ImageView showing the image response of a GetMap request. */
	private ImageView mGetMapView;
	/** A list of the Markers added to the map, sorted by the geomId of the geometry they represent. */
	private HashMap<String, Long> mMarkers = new HashMap<String, Long>();
	/** A Polyline through the selected points. */
	private Polyline mSelectedPolyline;
	
	/** A list of the ID:s of all OverlayItems that have been selected (to combine into a line/polygon feature). */
	private ArrayList<Long> mSelected = new ArrayList<Long>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate(Bundle) called.");		
		super.onCreate(savedInstanceState);
		
		/* Restore state if there is one. */
		if(savedInstanceState != null) {
			/* Fetch any previously selected points back into mSelected. */
			long[] array = savedInstanceState.getLongArray("mSelected");
			if(array != null)
				mSelected = new ArrayList<Long>(Arrays.asList(Utilities.longToObjectArray(array)));
			/* Fetch the flag indicating whether or not to restart requesting Location Updates. */
			mLocating = savedInstanceState.getBoolean("mLocating");
		}
		
		setContentView(R.layout.mapviewer);
		
		mGetMapView = (ImageView) findViewById(R.id.mapviewer_mapimage);
		mLocationMessageView = (TextView) findViewById(R.id.mapviewer_textview_locationmsg);
		
		mMapView = (MapView) findViewById(R.id.mapviewer_gmapview);
		mMapView.onCreate(savedInstanceState); // Forward the onCreate() call to the MapView object.
		
		/* Initialize the Maps API to make sure the BitmapFactory class can be used. */
		try {
			MapsInitializer.initialize(getApplicationContext());
		} catch (GooglePlayServicesNotAvailableException e) {
			noGooglePlaySvc(); // If the Google Play services are not available, go back to MainMenu where a new check will be performed.
		}
	}	

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == LOCATIONCLIENT_CONNECTION_REQUEST) {
			Log.v(TAG, "onActivityResult(requestCode=" + requestCode + ", resultCode=" + resultCode + ", Intent) called.");
			switch (resultCode) {
				case Activity.RESULT_OK:
					Log.i(TAG, "LocationClient connection fixed by the user. Connecting again.");
					mLocationClient.connect();
					break;
				default: // The LocationClient can still not be connected to.
					Log.i(TAG, "LocationClient connection was not fixed by the user.");
					AlertDialog alertDialog = new AlertDialog.Builder(this).create();
					alertDialog.setMessage(getString(R.string.app_gplaysvc_required));
					
					/* Add a button to the dialog and set its text and button listener. */
					alertDialog.setButton(getString(R.string.service_alert_buttontext_ok), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							// Do nothing, only for user information.
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
		/* Bind to the BackboneSvc Service. */
		Intent serviceIntent = new Intent(MapViewer.this, BackboneSvc.class);		        
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        
		super.onResume();
		mMapView.onResume(); // Forward the onResume() call to the MapView object.

		setUpMapIfNeeded();
		setUpLocationClientIfNeeded();
	}

	/**
	 * Continuation of the create/resume process, called from onServiceConnected
	 * after having bound to the Service.
	 */
	protected void onBound() {
		Log.d(TAG, "onBound() called.");
		mService.setActiveActivity(MapViewer.this);
		
		findViewById(R.id.mapviewer_webconnection).setAnimation(mService.getAnimationNoQueue()); // Pass the RotateAnimation to the webconnection View.
		
		saveOrRestoreUserLocation(false); // Restore the user's map camera position.
		
		refreshGeometry(); // Clear and recreate Markers and Polylines.
		
		/* Make a GetMap request if needed, and display the results on top of the MapView. */
		try {
			showMapImage(mService.getActiveGetMap().getImage()); // Try to show the last image returned if there is one.
		} catch (NullPointerException e) {
			Log.i(TAG, "No image found: Attempt a GetMap request. " + e.toString());
			if(mService.getActiveServer() != null) {
				mService.makeGetMapRequest(mService.getActiveServer(), mMapView, toBounds()); // Otherwise, make a new GetMap request and display its result instead.
			}
			else
				Log.i(TAG, "No active server connection.");
		}

		if(mLocating) // Restart Location Updates if they were being requested before the Activity was restarted.
			startUpdates(false);
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause() called.");		
		unbindService(mServiceConnection);
		
		saveOrRestoreUserLocation(true); // Store the user's map camera position.
		
		stopUpdates(); // Stop listening for updates.
		
		mMapView.onPause(); // Forward the onPause() call to the MapView object.
		super.onPause();		
	}
	
	@Override
	protected void onDestroy() {
		mMapView.onDestroy(); // Forward the onDestroy() call to the MapView object.
		super.onDestroy();		
	}
	
	@Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory(); // Forward the onLowMemory() call to the MapView object.
    }
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLongArray("mSelected", Utilities.longToPrimitiveArray(mSelected.toArray(new Long[mSelected.size()])));
        outState.putBoolean("mLocating", mLocating);
        mMapView.onSaveInstanceState(outState);
    }
	
	/**
	 * Saves or restores the users map camera position
	 * to/from the SharedPreferences.
	 * @param save True to save, false to restore.
	 */
	private void saveOrRestoreUserLocation(boolean save) {
		if(save) {
			if(mMap != null) { // If there is a GoogleMap object, store the user's location in the SharedPreferences.
				getSharedPreferences(getString(R.string.app_name_short) + "_preferences", MODE_PRIVATE).edit()
						.putFloat("zoom", mMap.getCameraPosition().zoom)
						.putFloat("latitude", (float) mMap.getCameraPosition().target.latitude) // Approximate position is acceptable, so ignore truncation.
						.putFloat("longitude", (float) mMap.getCameraPosition().target.longitude) // Approximate position is acceptable, so ignore truncation.
						.commit(); 
			}
		}
		else {
			/* Restore the user's last position from the SharedPreferences. */
			SharedPreferences userpos = getSharedPreferences(getString(R.string.app_name_short) + "_preferences", MODE_PRIVATE);
			zoomToLocation(new LatLng(
					(double) userpos.getFloat("latitude", (float) 55.709),
					(double) userpos.getFloat("longitude", (float) 13.201)),
					userpos.getFloat("zoom", (float) 3),
					false);
		}		
	}

	/** Sets the GoogleMap unless it has already been set. */
	private void setUpMapIfNeeded() {
	    if (mMap == null) { // Check if the GoogleMap needs to be setup.
	        mMap = mMapView.getMap();
	        if (mMap != null) { // The Map is verified. It is now safe to manipulate the map.
	        	mMap.setOnCameraChangeListener(this); // Set this Activity as the OnCameraChangeListener for the map so that any movements in the map (zooming, panning etc.) causes a callback to onCameraChange(CameraPosition).
	        	mMap.setOnMapLongClickListener(this); // Set this Activity as the OnLongClickListener for the map so that any Long Presses by the user causes a callback to onMapLongClick(LatLng).
	        	mMap.setOnMarkerClickListener(this); // Set this Activity as the OnMarkerClickListener for the map so that any Clicks by the user on a map Marker causes a callback to onMarkerClick(Marker).
	        }
	        else
	        	noGooglePlaySvc(); // If the Google Play services are not available, go back to MainMenu where a new check will be performed.
	    }
	}
	
	/** Sets the LocationClient unless it has already been set. */
	private void setUpLocationClientIfNeeded() {
	    if (mLocationClient == null) { // Check if the LocationClient needs to be setup.
	    	mLocationClient = new LocationClient(getApplicationContext(), this, this);
	    }
	  }

	/**
	 * Callback method called when the user clicks the mLocationMessageView
	 * TextView. Stops listening for Location Updates and saves the location.
	 */
	public void onClickLocationMessage(View source) {
		Log.d(TAG, "onClickLocationMessage() called on location: " + Utilities.coordinateToString(mLocation.getLatitude(), mLocation.getLongitude()));
		stopUpdates(); // Stop requesting Location Updates.
		mLocateTimer.cancel(); // No need to wait anymore.
		showAcceptLocationDialog(new LatLng(mLocation.getLatitude(), mLocation.getLongitude()), mLocation.getAccuracy(), true); // Ask whether or not to accept the selected point.
	}
	
	/**
	 * Called when clicking the satellite/map-button.
	 * Toggles between satellite images and the 'Normal' Google Maps
	 * map type in the MapView.
	 * @param satmapButtonView The source view of the click.
	 */
	public void onClickSatMap(View satmapButtonView) {
		Log.d(TAG, "onClickSatMap(View) called.");
		ToggleButton source = (ToggleButton) satmapButtonView; // Get the Satellite Map button reference.
		if(source.isChecked()) // If the button was checked, turn on Satellite Maps.
			mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
		else // Else, turn on the 'Normal' map.
			mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.d(TAG, "onCreateOptionsMenu(Menu) called.");
	    if(mService.getActiveLayer() == null) { // Without an active layer there should be no options menu.
	    	Log.i(TAG, "Will not create options menu without an active layer.");
	    	return false;
	    }

	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.mapviewer_optionsmenu, menu);
	     
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		Log.d(TAG, "onPrepareOptionsMenu(Menu) called.");
		boolean sequenceType = (mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_POINT || mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_INVALID) ? false : true;
		
		/* Hide the buttons for making lines/polygons if the active layer is not of those types,
		 * or doesn't contain any geometry.
		 * Also; only show one of them, depending on if the user is combining points or not. */
		int reqPoints = (mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_LINE) 	?	2	:
						(mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_POLYGON)?	3	:	0;
		boolean enoughPoints = (reqPoints <= mService.getActiveLayer().getGeometry().size());
	    menu.findItem(R.id.mapviewer_optionsmenu_selectsequence).setVisible(sequenceType && !mService.mCombining && enoughPoints).setEnabled(sequenceType && !mService.mCombining && enoughPoints);
	    menu.findItem(R.id.mapviewer_optionsmenu_combinesequence).setVisible(sequenceType && mService.mCombining && enoughPoints).setEnabled(sequenceType && mService.mCombining && enoughPoints);
	    menu.findItem(R.id.mapviewer_optionsmenu_clearselection).setVisible(sequenceType && mService.mCombining && enoughPoints).setEnabled(sequenceType && mService.mCombining && enoughPoints);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.d(TAG, "onOptionsItemSelected(MenuItem=" + item.getTitle() + ") called.");
		switch(item.getItemId()) {
			case R.id.mapviewer_optionsmenu_addposition: {
				onClickOptionsAddPosition();
				break;
			}
			case R.id.mapviewer_optionsmenu_manualadd: {
				onClickOptionsManualAdd();
				break;
			}
			case R.id.mapviewer_optionsmenu_selectsequence: {
				onClickOptionsSelectSequence();
				break;
			}
			case R.id.mapviewer_optionsmenu_combinesequence: {
				onClickOptionsCombineSequence();
				break;
			}
			case R.id.mapviewer_optionsmenu_clearselection: {
				onClickOptionsClearSelection();
				break;
			}
		}
		return super.onOptionsItemSelected(item);
	}
	
	/**
	 * Called when the add position button is clicked in the options menu of MapViewer.
	 * Will request the current geographical position from the device and add it to
	 * the currently active layer's geography.
	 */
	public void onClickOptionsAddPosition() {
		Log.d(TAG, "onClickOptionsAddPosition() called.");
		startUpdates(true);
	}
	
	/**
	 * Called when the manual add button is clicked in the options menu of MapViewer.
	 * Will let the user manually enter coordinates of a location and add it to
	 * the currently active layer's geography.
	 */
	public void onClickOptionsManualAdd() {
		Log.d(TAG, "onClickOptionsManualAdd() called.");
		int numFields = (mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_POINT) ? mService.getActiveLayer().getNonGeomFields().size() : 0; // No attribute fields should be displayed unless it's a Point layer.
		Intent intentAtt = new Intent(mService.getActiveActivity(), AttributeEditor.class);
		intentAtt.putExtras(AttributeEditor.fillBundle(null, mService.getActiveLayer().getNewId(), numFields, true, null, true));
        startActivity(intentAtt);
	}

	/**
	 * Called when the select sequence button is clicked in the options menu of MapViewer.
	 * Will let the user select points to combine into a line or a polygon.
	 */
	public void onClickOptionsSelectSequence() {
		Log.d(TAG, "onClickOptionsSelectSequence() called.");
		
		if(!mService.getActiveLayer().hasGeometry()) {
			mService.showToast(R.string.mapviewer_selectsequence_nopoints);
			return;
		}
		mService.mCombining = true;
		mService.showToast(R.string.mapviewer_selectsequence_taptoselect);
		Log.i(TAG, "Now combining points.");
	}
	
	/**
	 * Called when the combine sequence button is clicked in the options menu of MapViewer.
	 * Will store the selected points as a line or a polygon if valid.
	 */
	public void onClickOptionsCombineSequence() {
		Log.d(TAG, "onClickOptionsCombineSequence() called. No longer combining points.");
		
		mService.mCombining = false; // Stop combining points regardless of the result.
		
		/* If there are not enough selected points, notify the user. */
		int reqPoints = (mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_POINT) 		?	1	:
						(mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_LINE) 		?	2	:
						(mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_POLYGON) 	?	3	:	0;
		if(mSelected.size() < reqPoints) {
				Log.w(TAG, "Not enough points selected for this type of geometry.");
				mService.showToast(R.string.mapviewer_combinesequence_noselected);
		}
		/* Else, test the geometry according to the type of layer, and if valid, add it to the layer's point sequences. */
		else {
			Coordinate[] coordinates = new Coordinate[mSelected.size()];
				
//			Log.v(TAG, "mSelected.size(): " + mSelected.size());
			String coordString = "";
			for(int i=0; i < mSelected.size(); i++) {	
				LatLng point = mService.getActiveLayer().getGeometry().get(mSelected.get(i));
				coordinates[i] = new Coordinate(point.longitude, point.latitude);
				coordString = coordString + coordinates[i].toString() + ", ";
			}
			Log.v(TAG, "Coordinates: " + coordString);	
				
			Long newId = (mService.getActiveLayer().getPointSequence().size() > 0) ? mService.getActiveLayer().getPointSequence().lastKey() + 1 : 1;
			try {
				if(mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_POLYGON)
					mService.getActiveLayer().addPolygon(mService.getGeometryFactory().createPolygon(mService.getGeometryFactory().createLinearRing(coordinates), null), newId, false);
				else
					mService.getActiveLayer().addLine(mService.getGeometryFactory().createLineString(coordinates), newId, false);
				mService.getActiveLayer().getPointSequence().put(newId, new ArrayList<Long>(mSelected));
					
				/* If successfully added, launch the attribute editor for this geometry. */
				Intent intentAtt = new Intent(mService.getActiveActivity(), AttributeEditor.class);
				intentAtt.putExtras(AttributeEditor.fillBundle(null, newId, mService.getActiveLayer().getNonGeomFields().size(), false, null, true));
				startActivity(intentAtt);
			} catch (Exception e) {
				Log.w(TAG, e.toString());
				mService.showToast(R.string.mapviewer_combinesequence_invalid); // Notice the user of invalid point sequences.
			}
		}
			
		/* Regardless of the result; clear the selection and refresh the overlay. */
		mSelected.clear();
		refreshGeometry();
	}

	/**
	 * Called when the clear selection button is clicked in the options menu of MapViewer.
	 * Will clear the point selection and refresh Markers and Polylines.
	 */
	public void onClickOptionsClearSelection() {
//		Log.d(TAG, "onClickOptionsClearSelection() called. No longer combining points.");
		
		mService.mCombining = false; // Stop combining points regardless of the result.
		mSelected.clear(); // Clear the selection.
		refreshGeometry(); // Have to refresh the Geometry as there is no way to get the selected Markers without keeping the actual Marker references, which can change during the Activity life cycle.
	}

	public void onCameraChange(CameraPosition position) {
		if(mService != null) {
			if(mService.getActiveGetMap() != null)
				mService.getActiveGetMap().cancel(true);
			if(mService.getActiveServer() != null)
				mService.makeGetMapRequest(mService.getActiveServer(), mMapView, toBounds());		
		}
	}

	/**
	 * Method for displaying an image over the Google Maps background
	 * of the MapViewer Activity.
	 * @param image The Bitmap image to display.
	 */
	public void showMapImage(Bitmap image) {
//		Log.d(TAG, "showMapImage(Bitmap) called.");

		if(image != null)
			Log.i(TAG, "Displaying GetMap image.");
		else
			Log.i(TAG, "Removed GetMap image.");
		
		try {
			mGetMapView.setImageBitmap(image);
		} catch (NullPointerException e) {
			Log.e(TAG, e.toString());
		}
	}
	
	/**
	 * Clears current map objects and displays the active layer's geometry
	 * as Markers, with Polylines where there are point sequences.
	 */
	public void refreshGeometry() {
		Log.d(TAG, "refreshGeometry() called.");
		
		/* Clear the map to make room for the new Markers and Polylines. */
    	mMap.clear();
    	mSelectedPolyline = null;
    	mMarkers.clear();
    	
		/* If there's an active layer: add the geometry. */
        if(mService.getActiveLayer() != null) {        					
        	/* Setup the Markers for Point geometries. */
        	if(mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_POINT) {
        		Log.i(TAG, "Showing geometry of a Point layer.");
        						
        		for(Long key : mService.getActiveLayer().getGeometry().keySet()) { // Go through all the geometries' keys to access the geometries and their attributes.
        			LatLng location = mService.getActiveLayer().getGeometry().get(key);
        			String snippet = Utilities.coordinateToString(location.latitude, location.longitude);

        			/* Iterate over all the fields of the layer to add any attributes of this geometry to the Marker snippet. */
        			Iterator<LayerField> fieldit = mService.getActiveLayer().getFields().iterator();
        			while(fieldit.hasNext()) {
        				String attr = mService.getActiveLayer().getAttributes().get(key).get(fieldit.next().getName());
        				if(attr != null)
        					snippet = snippet + "\n\n" + attr;
        			}

        			addMarker(key, location, snippet, mSelected.contains(key)); // Add the point as a Marker. If the point is selected, use the corresponding icon.
        		}
        	}
        	/* Setup Markers and Polylines for Line or Polygon geometries. */
        	else {
        		Log.i(TAG, "Showing geometry of a non-Point layer."); 

        		@SuppressWarnings("unchecked")
        		TreeMap<Long, LatLng> clone = (TreeMap<Long, LatLng>) mService.getActiveLayer().getGeometry().clone(); // Make a clone of the geometries to keep track of which geometries are already drawn.
//        		Log.v(TAG, "Clone start size: " + clone.size() + ". Geometries: " + mService.getActiveLayer().getGeometry().size() + ".");
        		/* Add the points in point sequences, with the same attributes for each point in the same sequence. */
        		for(Long key : mService.getActiveLayer().getPointSequence().keySet()) { // Go through all the point sequences and their attributes.
        			for(Long p : mService.getActiveLayer().getPointSequence().get(key)) { // For each point in this point sequence.
//            			Log.v(TAG, "Clone size: " + clone.size() + ". Added sequenced point with attributes.");
        				LatLng location = mService.getActiveLayer().getGeometry().get(p);
            			clone.remove(p);
            			String snippet = Utilities.coordinateToString(location.latitude, location.longitude);

            			/* Iterate over all the fields of the layer to add any attributes of this geometry to the Marker snippet. */
            			Iterator<LayerField> fieldit = mService.getActiveLayer().getFields().iterator();
            			while(fieldit.hasNext()) {
            				String attr = mService.getActiveLayer().getAttributes().get(key).get(fieldit.next().getName());
            				if(attr != null)
            					snippet = snippet + "\n\n" + attr;
            			}
                						
            			addMarker(p, location, snippet, mSelected.contains(p)); // Add the point as a Marker. If the point is selected, use the corresponding icon.
        			}
        		}
        		/* Add the points not belonging to any point sequence, without attributes. */
        		for(Long id : clone.keySet()) {
        			Log.v(TAG, "Adding unsequenced point without attributes.");
        			LatLng p = clone.get(id);
        			String snippet = Utilities.coordinateToString(p.latitude, p.longitude);
        			addMarker(id, p, snippet, mSelected.contains(id)); // Add the point as a Marker. If the point is selected, use the corresponding icon.
        		}
        		
        		/* Add Polylines to all point sequences. */
        		for(Long seq : mService.getActiveLayer().getPointSequence().keySet()) {
        			PolylineOptions options = new PolylineOptions()
        					.color(Color.BLACK)
        					.width(6)
        					.zIndex(3);
        			for(Long p : mService.getActiveLayer().getPointSequence().get(seq))
        				options.add(mService.getActiveLayer().getGeometry().get(p));
        			mMap.addPolyline(options); // Add the new Polyline according to the options.
        		}
        		/* Add a Polyline to the selected points. */
        		PolylineOptions selOptions = new PolylineOptions()
						.color(Color.GRAY)
						.width(8)
						.zIndex(4);
        		for(Long p : mSelected)
        			selOptions.add(mService.getActiveLayer().getGeometry().get(p));
        		mSelectedPolyline = mMap.addPolyline(selOptions); // Add the new Selected Polyline according to the options and save its reference for future editing.
        	}        	
        }
	}
	
	/**
	 * Zooms to the target location and zoom level with animation.
	 * @param target The location to zoom to.
	 * @param zoomLevel The zoom level to set. Use -1 for no change.
	 * @param animate true to animate the map change instead of moving instantaneously.
	 */
	private void zoomToLocation(LatLng target, float zoomLevel, boolean animate) {
		Log.d(TAG, "zoomToLocation(Location, int) called.");
		if(mMap != null) {
			if(zoomLevel != -1)
				if(animate)
					mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, zoomLevel));
				else
					mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, zoomLevel));
			else {
				if(animate)
					mMap.animateCamera(CameraUpdateFactory.newLatLng(target));
				else
					mMap.moveCamera(CameraUpdateFactory.newLatLng(target));
			}
		}
		else
			Log.e(TAG, "GoogleMap object unavailable, cannot manipulate map.");
			
	}
	
	/**
	 * Gives the current bounding box of the map as a LatLngBounds
	 * object with the southwest and northeast corner coordinates in degrees.
	 * @return The current map LatLngBounds bounding box.
	 */
	public LatLngBounds toBounds() {
		Projection proj = mMap.getProjection();
		LatLng southWest = proj.fromScreenLocation(new android.graphics.Point(0, mMapView.getHeight()));
		LatLng northEast = proj.fromScreenLocation(new android.graphics.Point(mMapView.getWidth(), 0));
		Log.d(TAG, "toBounds() called: " + northEast.latitude + " N, " + northEast.longitude + " E, " + southWest.latitude + " S, " + southWest.longitude + " W.");
		return new LatLngBounds(southWest, northEast);
	}

	/**
	 * Callback method called when the user performs a Long Press
	 * on the map displayed by this Activity. Lets the user choose
	 * whether or not to save to Long Pressed location.
	 * @param point The LatLng point where the user Long Pressed the map.
	 */
	public void onMapLongClick(LatLng point) {
		Log.d(TAG, "onMapLongClick(" + Utilities.coordinateToString(point.latitude, point.longitude) + ") called.");
		if(mService.getActiveLayer() != null) { // There must be an active layer to add points to.
			Log.v(TAG, "Long press allowed, ask to add location.");
			showAcceptLocationDialog(point, -1, false);
		}
		
	}

	/**
	 * Callback method called when the user clicks on a map
	 * Marker. Displays the Marker's position and any
	 * attributes stored for the respective geometry.
	 * @param marker The marker that was clicked.
	 * @return true if the listener has consumed the event (i.e., the default behavior should not occur), false otherwise (i.e., the default behavior should occur). The default behavior is for the camera to move to the map and an info window to appear.
	 */
	public boolean onMarkerClick(Marker marker) {
		try {
			if ((mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_LINE || mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_POLYGON) && mService.mCombining){ // If the Active layer is of the line/polygon types and point selection is enabled.
				Long geomId = mMarkers.get(marker.getId());
				if(!mSelected.contains(geomId) || geomId == mSelected.get(0)) { // Don't allow already selected points to be selected again unless it's the first one (forming a ring).
					mSelected.add(geomId);
					addMarker(geomId, marker.getPosition(), marker.getSnippet(), true);
					removeMarker(marker);
					List<LatLng> points = mSelectedPolyline.getPoints(); // Get the list of points from the Polyline.
					points.add(mService.getActiveLayer().getGeometry().get(geomId)); // Add the selected point to the list.
					mSelectedPolyline.remove(); // Remove the old Polyline.
					mSelectedPolyline = mMap.addPolyline(new PolylineOptions()
							.color(Color.GRAY)
							.width(8)
							.zIndex(4)
							.addAll(points)); // Add a new selected Polyline with the new point added.
				}
				Log.i(TAG, "Geometry (id: " + geomId + ") selected.");
			}
			else if(!mService.mCombining) // Allow display of attributes etc. when not combining points.
				showAttributeDialog(marker);
		} catch (NullPointerException e) { Log.e(TAG, e.toString()); }

		return true;
	}

	/**
	 * Adds a Marker with the specified snippet and the default
	 * icon, unless the selected parameter is true in which case
	 * the icon for selected points will be used.
	 * @param geomId The ID of the Geometry for which the Marker is being added.
	 * @param pos The LatLng location of the Marker.
	 * @param snippet The snippet (message) to display when clicking the Marker.
	 * @param selected Whether or not the 'selected' icon should be used.
	 */
	private void addMarker(Long geomId, LatLng pos, String snippet, boolean selected) {
		String markerId = mMap.addMarker(new MarkerOptions()
				.position(pos)
				.title(getString(R.string.mapviewer_attdialog_title))
				.snippet(snippet)
				.icon(BitmapDescriptorFactory.fromResource(
					(selected) ? R.drawable.location_selected : R.drawable.location_place))
			).getId();
		mMarkers.put(markerId, geomId);
	}
	
	/**
	 * Removes the specified marker. Used to make sure that
	 * the reference in mMarkers is removed as well.
	 * @param marker The marker to remove.
	 * @see #mMarkers The map of Marker references.
	 */
	private void removeMarker(Marker marker) {
		mMarkers.remove(marker.getId());
		marker.remove();
	}
	
	/**
	 * Displays a Dialog letting the user's decide whether or
	 * not to save a specified location.
	 * @param location The LatLng location which should or should not be saved.
	 * @param accuracy The accuracy with which the location was found. Pass -1 to not include accuracy in the Dialog.
	 * @param zoomToLocation Pass true to pan to the location if the user accepts it. 
	 */
	private void showAcceptLocationDialog(LatLng location, float accuracy, boolean panToLocation) {
		final LatLng loc = location;
		final float acc = accuracy;
		final boolean pan = panToLocation;
			
		/* Show a dialog with the new location coordinates and let the user accept or discard this as a feature point. */
		new AlertDialog.Builder(mService.getActiveActivity())
				.setIcon(android.R.drawable.ic_dialog_map)
				.setTitle(R.string.mapviewer_addpositiondialog_title)
				.setMessage(new String(
						mService.getString(R.string.mapviewer_addpositiondialog_question) +
						"\n" +
						Utilities.coordinateToString(loc.latitude, loc.longitude) +
						((acc != -1) ?
						"\n" + getString(R.string.mapviewer_addposition_locationmsg_acc) + " " + acc
						: "")))
				.setPositiveButton(R.string.mapviewer_addpositiondialog_accept, new DialogInterface.OnClickListener() {
	        		public void onClick(DialogInterface dialog, int which) {
	        			Log.d(TAG, "PositiveButton.onClick() called. Saving position.");
	        			LatLng point = new LatLng(loc.latitude, loc.longitude);
	        			long newGeomId = mService.getActiveLayer().addGeometry(point); // Save the pressed location.
	        			addMarker(newGeomId, point, Utilities.coordinateToString(point.latitude, point.longitude), false); // Display the new point as a Marker.

	        			if(pan)
	        				zoomToLocation(point, -1, true); // Zoom to the location of the selected fix.
	    				
	        			/* If a point layer is active and has at least one attribute: start the AttributeEditor allowing attributes to be entered. */
	        			if(mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_POINT && mService.getActiveLayer().getNonGeomFields().size() >= 1) {
	        				Log.v(TAG, "Attributes requested.");
	        				Intent intentAtt = new Intent(mService.getActiveActivity(), AttributeEditor.class);
	        				intentAtt.putExtras(AttributeEditor.fillBundle(null, newGeomId, mService.getActiveLayer().getNonGeomFields().size(), false, null, true));
	        				mService.getActiveActivity().startActivity(intentAtt);
	        			}
	        			dialog.dismiss();    
	        		}})
	        	.setNegativeButton(R.string.mapviewer_addpositiondialog_decline, null)
	        	.show();
	}

	/**
	 * Show a dialog displaying a Marker's attributes and
	 * letting the user choose to edit them.
	 * @param marker The Marker that the user clicked and wants to edit attributes for.
	 */
	private void showAttributeDialog(Marker marker) {
		final long geomId = mMarkers.get(marker.getId());
		final long sequencedId = mService.getActiveLayer().pointInSequence(geomId);
		final Marker m = marker;
		final boolean pointLayer = (mService.getActiveLayer().getTypeMode() == GeographyLayer.TYPE_POINT);
		
		AlertDialog.Builder dialog = new AlertDialog.Builder(this)
				.setTitle(m.getTitle())
				.setMessage(m.getSnippet());
			
		/* Only allow attribute editing if the layer is of the Point type or if the selected point is included in a sequence (which has attributes). */
		if(pointLayer || sequencedId != -1) {
			dialog.setPositiveButton(R.string.mapviewer_attdialog_editbutton, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					/* Launch the attribute editor for the geometry associated with this item. */
					Intent intentAtt = new Intent(mService.getActiveActivity(), AttributeEditor.class);
					intentAtt.putExtras(AttributeEditor.fillBundle(null,
							(pointLayer) ? geomId : sequencedId,
							mService.getActiveLayer().getNonGeomFields().size(),
							(pointLayer) ? true : false, null, true)); // If the geometry is of the Point type, also allow manual editing of the coordinate.
					startActivity(intentAtt);
				}				
			});
		}		
		/* Allow editing of point coordinates for Line/Polygon type geometries if the selected point is not included in a sequence. */
		if(!pointLayer && sequencedId == -1) {
			dialog.setPositiveButton(R.string.mapviewer_attdialog_editbutton, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					/* Launch the attribute editor for the geometry associated with this item. */
					Intent intentAtt = new Intent(mService.getActiveActivity(), AttributeEditor.class);
					intentAtt.putExtras(AttributeEditor.fillBundle(null, geomId, 0, true, null, true)); // Allow editing only of the coordinates.
					startActivity(intentAtt);
				}				
			});
		}

		dialog.setNegativeButton(R.string.mapviewer_attdialog_deletebutton, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				/* Delete the geometry and attributes associated with this item. */
				try {
					/* If the layer is of the point type, or it's not included in any sequence; remove the point and its attributes. */
					if(pointLayer || sequencedId == -1) {
						mService.getActiveLayer().getGeometry().remove(geomId);
						mService.getActiveLayer().getAttributes().remove(geomId);
						removeMarker(m);
					}
					/* Otherwise, remove the sequence and its attributes while letting its points remain. (A sequenced point was selected.) */
					else {
						mService.getActiveLayer().getPointSequence().remove(sequencedId);
						mService.getActiveLayer().getAttributes().remove(sequencedId);
						refreshGeometry(); // Refresh Markers and Polylines since the specific Polyline cannot be accessed.
					}
				} catch (NullPointerException e) { Log.e(TAG, "No such geometry. " + e.toString()); }
			}
		});

		dialog.show();
	}
	
	/**
	 * Callback method called when a new location is found.
	 * Edits the text in the mLocationMessageView TextView to
	 * reflect the new position.
	 */
	public void onLocationChanged(Location location) {
		mLocation = location; // Save the user's last known location.
		mLocationMessageView.setText(
				Utilities.coordinateToString(location.getLatitude(), location.getLongitude()) +
				"\n" +
				getString(R.string.mapviewer_addposition_locationmsg_acc) +
				" " +
				location.getAccuracy() +
				"\n" +
				getString(R.string.mapviewer_addposition_locationmsg_tap));
		zoomToLocation(new LatLng(location.getLatitude(), location.getLongitude()), -1, true);
	}

	public void onConnected(Bundle connectionHint) {
		mLocationClient.requestLocationUpdates(LOCATION_REQUEST, this); // Start requesting Location Updates.
		mLocating = true; // Signal that Location Updates are being requested.

		/* Start a timer after after which to stop listening for location updates. */
		mLocateTimer = new Timer();
        mLocateTimer.schedule(new TimerTask() {
        	public void run() {
        		mService.getActiveActivity().runOnUiThread(
        		new Runnable() {
        			public void run() {
        				/* The following will be queued on the main (UI) thread. */
        				stopUpdates(); // Stop receiving Location Updates.
                		Log.i(TAG, "No location selected, suggest the last one.");
                		showAcceptLocationDialog(new LatLng(mLocation.getLatitude(), mLocation.getLongitude()), mLocation.getAccuracy(), true);
        			}
        		});
                this.cancel(); //Terminate the timer thread.
            }
        }, LOCATION_REQUEST_TIMEOUT);
	}

	public void onDisconnected() {
		mService.showToast(R.string.mapviewer_addposition_stopped);
	}

	public void onConnectionFailed(ConnectionResult result) {
		try {
			result.startResolutionForResult(this, LOCATIONCLIENT_CONNECTION_REQUEST);
		} catch (SendIntentException e) {
			e.printStackTrace();
			noGooglePlaySvc();
		}
	}
	
	/**
	 * Starts requesting Location Updates, or sends
	 * the user to the system's Location Settings if
	 * the GPS is disabled.
	 * @param alert true to display a Toast informing the user.
	 */
	private void startUpdates(boolean alert) {
//		Log.d(TAG, "startUpdates(alert=" + String.valueOf(alert) + ") called.");
		/* Check if the GPS provider is enabled, otherwise alert the user and send the user to the settings. */
		if(((LocationManager) getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			mLocationClient.connect(); // Connect the LocationClient which, when it finishes, will call onConnected() where the LocationUpdates request can be made.
			if(alert)
				mService.showToast(R.string.mapviewer_addposition_gettinglocation);
		}
		else{
			/* Create an AlertDialog to inform the user, which upon pressing "ok"
	    	 * will send the user the the system's Location Settings. */
	    	AlertDialog alertDialog = new AlertDialog.Builder(this).create();
			alertDialog.setMessage(getString(R.string.mapviewer_addposition_gpsmissing));		
			/* Add a button to the dialog and set its text and button listener. */
			alertDialog.setButton(getString(R.string.service_alert_buttontext_ok), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); // Send the user to the system's Location Settings to enable the GPS source.
					dialog.dismiss();    		      
				}
			});		
			alertDialog.show(); // Display the dialog to the user.
		}
	}
	
	/**
	 * Stops listening for Location Updates and removes
	 * any text in mLocationMessageView showing the user's
	 * location.
	 */
	private void stopUpdates() {
		mLocating = false; // Signal that Location Updates have ceased.
		if(mLocateTimer != null)
			mLocateTimer.cancel(); // There is no need to wait if the Location Updates are already stopping.
		if(mLocationClient != null) { // If there is no LocationClient there is no connection.
			if(mLocationClient.isConnected()) {
				mLocationClient.removeLocationUpdates(this); // Stop listening for Location Updates.
				mLocationClient.disconnect();
				mLocationMessageView.setText(""); // Remove the text showing Location Updates.
			}
		}
	}
	
	/**
	 * Should be called when the Google Play services are
	 * found unavailable, to notify the user and send the
	 * user back to the MainMenu Activity where new checks
	 * can be made.
	 */
	private void noGooglePlaySvc() {
		Log.d(TAG, "noGooglePlaySvc() called.");
		AlertDialog alertDialog = new AlertDialog.Builder(this).create();
		alertDialog.setMessage(getString(R.string.app_gplaysvc_notavailable));
		
		/* Add a button to the dialog and set its text and button listener. */
		alertDialog.setButton(getString(R.string.service_alert_buttontext_ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				finish();
				dialog.dismiss();    		      
			}
		});		
		alertDialog.show(); // Display the dialog to the user.
	}
}
