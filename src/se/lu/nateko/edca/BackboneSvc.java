package se.lu.nateko.edca;

import java.util.Date;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import se.lu.nateko.edca.svc.DescribeFeatureType;
import se.lu.nateko.edca.svc.GeoHelper;
import se.lu.nateko.edca.svc.GeographyLayer;
import se.lu.nateko.edca.svc.GetCapabilities;
import se.lu.nateko.edca.svc.GetMap;
import se.lu.nateko.edca.svc.LocalSQLDBhelper;
import se.lu.nateko.edca.svc.ServerConnection;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLngBounds;
import com.vividsolutions.jts.geom.GeometryFactory;

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
 * General purpose Service, keeps track of the currently active				*
 * ServerConnection, the active GeographyLayer, the HTTP connection and		*
 * most of the other application-wide information.							*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.91, 2013-07-24												*
 * 																			*
 ****************************************************************************/
public class BackboneSvc extends Service {
	/** The error tag for this Service. */
	public static final String TAG = "BackboneSvc";
	/** String initialized in onCreate(), storing the application's package name for easy access. This is used to e.g. determine the folder path to the applications locally stored data. */
	public static String PACKAGE_NAME;
	
	/** Constant identifying a non-specific Activity. */
	public static final int ACTIVITY_DEFAULT = 0;
	/** Constant identifying the ServerEditor Activity. */
	public static final int ACTIVITY_SERVEREDITOR = 1;
	/** Constant identifying the ServerViewer Activity. */
	public static final int ACTIVITY_SERVERVIEWER = 2;
	/** Constant identifying the LayerViewer Activity. */
	public static final int ACTIVITY_LAYERVIEWER = 3;
	/** Constant identifying the MapViewer Activity. */
	public static final int ACTIVITY_MAPVIEWER = 4;
	/** Constant identifying the AttributeEditor Activity. */
	public static final int ACTIVITY_ATTRIBUTEEDITOR = 5;
	
	/** Constant identifying that the Service is in a disconnected state, lacking an active ServerConnection. */
	public static final int DISCONNECTED = 0;
	/** Constant identifying that the Service is in a connected state, with an active ServerConnection that has been reached. */
	public static final int CONNECTED = 1;
	/** Constant identifying that the Service is in a connecting state, currently using an active ServerConnection to try to reach a server. */
	public static final int CONNECTING = 2;
	/** Flag keeping track of the Service's state of connectedness to a server. DISCONNECTED by default. */
	private int mConnectState = DISCONNECTED;
	/** Stores the row number in the local SQLite database which holds the server information currently being used, or most recently used, to connect to a server. */
	private Long mConnectingRow = (long) 0;
	/** Whether or not an upload is currently underway. */
	private boolean mUploading = false;
	/** Flag showing whether or not the user is currently allowed to select points for combining into sequences (lines or polygons). */
	public boolean mCombining = false;

	/** Flag passed to renewLastServerConnection(boolean) to allow it to run. Will be changed to false after running once to ensure that the method is not run repeatedly without the user's request. */
	public static boolean mInitialRenewSrvConnection = true;
	/** Checked in renewActiveLayer(boolean) and changed to false after running the method to ensure that it is only run once. */
	private boolean mInitialRenewLayer = true;
	
	/** The ServerConnection currently connected, or connecting, to. */
	private ServerConnection mActiveServer;
	/** The GetCapabilities object most recently created, to call the active server to request a list of layers and capabilities and to parse and report the response. */
	private GetCapabilities mActiveCapabilities;
	/** The GetMap object most recently created, to call the active server to request a map image and pass on the response. */
	private GetMap mActiveGetMap;
	/** The DescribeFeatureType task most recently created, which requests information about a layer on the geospatial server and reports it to create a new GeographyLayer. */
	private DescribeFeatureType mDescribeFeatureType;
	/** The GeoHelper most recently created, which handles saving to and loading from the external storage (e.g. sd-card) and uploading of data to the geospatial server. */
	private GeoHelper mGeoHelper;
	/** The active GeographyLayer, holding information about the layer being targeted for data collection as well as holding any collected data waiting for upload. */
	private GeographyLayer mActiveLayer;
	/** The current foreground Activity. Set in the onBound() method of each Activity. */
	private Activity mActiveActivity;
	/** The helper class used to access and edit the local SQLite database. */
	private LocalSQLDBhelper mSQLhelper;	
	/** A JTS 1.8 GeometryFactory that is kept for the lifetime of the application. It is used to create JTS 1.8 Geometry objects that wrap around coordinates. */
	private GeometryFactory mGeomFac = new GeometryFactory();
	/** An application-wide RotateAnimation that ensures that the symbol indicating communication with the geospatial server is animated similarly in each Activity. */
	private RotateAnimation mConnectionAnimation;
	/** Whether or not a thread is currently altering the RotateAnimation. */
	private boolean mAlteringAnimation = false;
	/** Constant defining the time it should take (in milliseconds) the RotateAnimation to complete one spin. */
	public static final int ROTATION_DURATION = 2000;
	/** Application-wide HttpClient stored for re-use. */
	private HttpClient mHttpClient = new DefaultHttpClient();
	/** Whether or not a thread is currently using the HttpClient to communicate with a server. */
	private boolean mHttpConnecting = false;
	
	/** Binder given to client Activities in order to access this Service. */
    private final IBinder mBinder = new SvcAccessor(this);

    /** If the user leaves the ServerEditor activity, any entered text is held here temporarily until the service is destroyed or a ServerConnection is saved. */
    public String[] mTempText;

	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate() called.");
		PACKAGE_NAME = getPackageName();
		
		/* Setup the animation which shows when a connection to the geospatial server is active. */
		mConnectionAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, (float) 0.5, Animation.RELATIVE_TO_SELF, (float) 0.5);
		mConnectionAnimation.setDuration(ROTATION_DURATION);
		mConnectionAnimation.setInterpolator(this, android.R.anim.linear_interpolator);
		mConnectionAnimation.setFillAfter(true);
		mConnectionAnimation.setRepeatCount(0);
		
		mSQLhelper = new LocalSQLDBhelper(this);
		mSQLhelper.open();
		super.onCreate();
	}

	@Override
	public IBinder onBind(Intent intent) {
//		Log.d(TAG, "onBind(Intent) called.");
		return mBinder; // Returns the specified interface.
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy() called.");
		mSQLhelper.close();
		/* Cancel any running ASyncTasks managed by the BackboneSvc. */
		if(getActiveCapabilities() != null)
			getActiveCapabilities().cancel(true);
		if(getActiveGetMap() != null)
			getActiveGetMap().cancel(true);
		if(mDescribeFeatureType != null)
			mDescribeFeatureType.cancel(true);
		if(mGeoHelper != null)
			mGeoHelper.cancel(true);
		
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "onStartCommand(Intent, int, int) called.");
		return super.onStartCommand(intent, flags, startId);
	}

	/**
	 * Set method to assign a ServerConnection object to be the currently active connection,
	 * and tries to connect with a getCapabilities request.
	 * @param srv The ServerConnection to be in use.
	 */
	public void setActiveServer(ServerConnection srv) {
//		Log.d(TAG, "setActiveServer(ServerConnection) called.");
		try {
			Log.i(TAG, "New active server '" + srv.getName() + "': " + srv.toString());
			mActiveServer = srv;
		} catch (NullPointerException e) {
			Log.w(TAG, e.toString());
			mActiveServer = null;
		}
	}
	
	/**
	 * Get method for retrieving the currently active ServerConnection object
	 * from the BackboneSvc.
	 * @return The active ServerConnection object.
	 */
	public ServerConnection getActiveServer() {
//		Log.d(TAG, "getActiveServer() called.");
		return mActiveServer;
	}
	
	/**
	 * Stores the GetCapabilities object relating to the active ServerConnection.
	 * @param getCap The active GetCapabilities object to set for this BackboneSvc.
	 */
	public void setActiveCapabilities(GetCapabilities getCap) {
//		Log.d(TAG, "setActiveCapabilities(GetCapabilities) called.");
		mActiveCapabilities = getCap;
	}
	
	/**
	 * Get method for retrieving the stored GetCapabilities object
	 * relating to the active ServerConnection from the BackboneSvc.
	 * @return The GetCapabilities object of the active ServerConnection.
	 */
	public GetCapabilities getActiveCapabilities() {
//		Log.d(TAG, "getActiveCapabilities() called.");
		return mActiveCapabilities;
	}
	
	/**
	 * Stores the GetMap object last created by the MapViewer.
	 * @param getMap The active GetMap object to set for this BackboneSvc.
	 */
	public void setActiveGetMap(GetMap getMap) {
//		Log.d(TAG, "setActiveGetMap(GetMap) called.");
		mActiveGetMap = getMap;
	}
	
	/**
	 * Get method for retrieving the GetMap object last
	 * created by the MapViewer from the BackboneSvc.
	 * @return The stored GetMap object.
	 */
	public GetMap getActiveGetMap() {
//		Log.d(TAG, "getActiveGetMap() called.");
		return mActiveGetMap;
	}
	
	/**
	 * Method that sets the reference to the currently active Activity to which
	 * results and alerts are posted.
	 * @param activeActivity The Activity which is currently visible.
	 */
	public void setActiveActivity(Activity activeActivity) {
//		Log.d(TAG, "setActiveActivity(Activity) called.");
		mActiveActivity = activeActivity;
		Log.v(TAG, "New active activity: " + activeActivity.getLocalClassName());
	}
	
	/**
	 * Method that gets the reference to the currently active Activity to which
	 * results and alerts are posted.
	 * @return The Activity which is currently visible.
	 */
	public Activity getActiveActivity() {
//		Log.d(TAG, "getActiveActivity() called.");
		return mActiveActivity;
	}
	
	/**
	 * Set method for the currently active local layer.
	 * @param layer The layer to activate.
	 */
	public void setActiveLayer(GeographyLayer layer) {
//		Log.d(TAG, "setActiveLayer(GeographyLayer) called.");
		mActiveLayer = layer;
		if(layer != null)
			Log.v(TAG, "New active layer: " + mActiveLayer.getName());
		else
			Log.v(TAG, "Active layer set to null.");
	}
	
	/**
	 * Get method for the currently active local layer.
	 * @return The currently active local layer.
	 */
	public GeographyLayer getActiveLayer() {
//		Log.d(TAG, "getActiveLayer() called.");
		return mActiveLayer;
	}
	
	/**
	 * Fetches the application-wide HttpClient instance.
	 * Can not be fetched again until unlockHttpClient() is called.
	 * Threads calling this method before unlockHttpClient() has
	 * been called will wait indefinitely in a spin-loop.
	 * @return The HttpClient
	 * @throws InterruptedException
	 */
	public synchronized HttpClient getHttpClient() throws InterruptedException {
//		Log.d(TAG, "getHttpClient() called.");
		while(isHttpConnecting()) {
			Log.v(TAG, "Thread " + Thread.currentThread().getId() + " waiting to fetch the HttpClient.");
			wait();
		}
		mHttpConnecting = true;
		Log.v(TAG, "HttpClient given to thread: " + Thread.currentThread().getId());
		return mHttpClient;
	}
	
	/**
	 * Sets whether or not an HTTP connection is ongoing, to prevent
	 * multiple uses of the HttpClient concurrently.
	 */
	public synchronized void unlockHttpClient() {
//		Log.d(TAG, "unlockHttpClient() called.");
		mHttpConnecting = false;
//		Log.v(TAG, "HttpClient unlocked by: " + Thread.currentThread().getId());
		notify();
	}
	
	/**
	 * Fetches the flag showing whether or not an HTTP connection is ongoing.
	 * @return True if an HTTP connection is ongoing.
	 */
	private boolean isHttpConnecting() {
//		Log.d(TAG, "isHttpConnecting() called.");
		return mHttpConnecting;
	}
	
	/**
	 * Should be called whenever server communication fails, requiring
	 * the user to reconnect. Clears the active ServerConnection and
	 * sets the state to disconnected. Also clears remote layers that
	 * can no longer be accessed.
	 * @param reportFailure True to show a dialog alerting the user of a failed connection attempt.
	 */
	public void clearConnection(boolean reportFailure) {
		Log.d(TAG, "reportFailedConnection(reportFailure=" + String.valueOf(reportFailure) + ") called.");
		/* Clear the active server since it cannot be contacted and notify the user. */
		setActiveServer(null);
		setConnectingRow((long) 0);
		setConnectState(BackboneSvc.DISCONNECTED);		
		clearRemoteLayers();
		updateLayoutOnState();
		if(reportFailure)
			showAlertDialog(getString(R.string.service_connectionfailed), null);
	}
	
	/**
	 * Sets the value of the connect state flag.
	 * Can be DISCONNECTED (0), CONNECTED (1) or CONNECTING (2).
	 * @param state The state to set.
	 */
	public void setConnectState(int state) {
		Log.v(TAG, "setConnectState(" + String.valueOf(state) + ") called.");
		mConnectState = state;
	}
	
	/**
	 * Gets the connect state of the BackboneSvc.
	 * Can be DISCONNECTED (0), CONNECTED (1) or CONNECTING (2).
	 * @return The connect state.
	 */
	public int getConnectState() {
//		Log.d(TAG, "getConnectState() called.");
		return mConnectState;
	}
	
	/**
	 * Sets which row (in the server table) that is currently being connected to.
	 * @param connectingRow The row being connected to.
	 */
	public void setConnectingRow(Long connectingRow) {
		Log.v(TAG, "setConnectingRow(" + String.valueOf(connectingRow) + ") called.");
		mConnectingRow = connectingRow;
	}
	
	/**
	 * Get method for the row (in the database table) that is currently being connected to.
	 * @return The row being connected to.
	 */
	public Long getConnectingRow() {
//		Log.d(TAG, "getConnectingRow() called.");
		return mConnectingRow;
	}
	
	/** Start the Service's RotateAnimation. */
	public void startAnimation() {
//		Log.d(TAG, "startAnimation() called.");
		/* Start the animation in a separate thread to avoid blocking more crucial threads. */
		new Thread(new Runnable() {
        	public void run() {
        		try {
        			Log.v(TAG, "Thread " + Thread.currentThread().getId() + " starting animation.");
        			getAnimation().setRepeatCount(Animation.INFINITE); // Start the animation showing that a web communicating thread is active.
        			unlockAnimation();
        		} catch (InterruptedException e) { Log.w(TAG, "Thread " + Thread.currentThread().getId() + " interrupted. " + e.toString()); }
        	}
        }).start();
	}
	
	/** Stop the Service's RotateAnimation. */
	public void stopAnimation() {
//		Log.d(TAG, "stopAnimation() called.");
		/* Stop the animation in a separate thread to avoid blocking more crucial threads. */
		new Thread(new Runnable() {
        	public void run() {
        		try {
        			Log.v(TAG, "Thread " + Thread.currentThread().getId() + " stopping animation.");
        			getAnimation().setRepeatCount(0); // Stop the animation showing that a web communicating thread has finished.
        			unlockAnimation();
        		} catch (InterruptedException e) { Log.w(TAG, "Thread " + Thread.currentThread().getId() + " interrupted. " + e.toString()); }
        	}
        }).start();
	}
	
	/**
	 * Fetches the application-wide rotation animation instance.
	 * Can not be fetched using this method again until unlockAnimation()
	 * is called. Threads calling this method before unlockAnimation() has
	 * been called will wait indefinitely in a spin-loop.
	 * @return The stored animation.
	 * @throws InterruptedException 
	 */
	public synchronized RotateAnimation getAnimation() throws InterruptedException {
//		Log.d(TAG, "getAnimation() called by thread: " + Thread.currentThread().getId());
		while(getAlteringAnimation()) {
			Log.i(TAG, "Thread " + Thread.currentThread().getId() + " waiting to fetch the RotateAnimation.");
			wait();
		}
		mAlteringAnimation = true;
//		Log.v(TAG, "RotateAnimation given to: " + Thread.currentThread().getId());
		return mConnectionAnimation;
	}
	
	/**
	 * Gets the RotateAnimation in a non-thread safe way,
	 * and should thus not be used to alter the animation in any way.
	 * @return The application-wide RotateAnimation.
	 */
	public RotateAnimation getAnimationNoQueue() {
//		Log.d(TAG, "getAnimationNoQueue() called.");
		return mConnectionAnimation;
	}
	
	/**
	 * Indicates that alteration of the animation is finished, allowing
	 * the first thread waiting to access the animation to proceed.
	 */
	public synchronized void unlockAnimation() {
//		Log.d(TAG, "unlockAnimation() called.");
		mAlteringAnimation = false;
//		Log.v(TAG, "RotateAnimation unlocked by: " + Thread.currentThread().getId());
		notify();
	}
	
	/**
	 * Fetches the flag showing whether or not the RotateAnimation is being altered.
	 * @return True if a thread is altering the animation.
	 */
	private boolean getAlteringAnimation() {
//		Log.d(TAG, "getAlteringAnimation() called.");
		return mAlteringAnimation;
	}
	
	/**
	 * Sets the flag indicating whether or not an upload process is underway.
	 * @param uploading True if uploading.
	 */
	public void setUploading(boolean uploading) {
//		Log.d(TAG, "setUploading(uploading=" + String.valueOf(uploading) + ") called.");
		mUploading = uploading;
	}
	
	/**
	 * Gets the flag indicating whether or not an upload process is underway.
	 * @return True if uploading.
	 */
	public boolean getUploading() {
//		Log.d(TAG, "getUploading() called.");
		return mUploading;
	}
	
	/**
	 * Get method for retrieving the local SQLite database helper
	 * from the BackboneSvc.
	 * @return The active ServerConnection object.
	 */
	public LocalSQLDBhelper getSQLhelper() {
//		Log.d(TAG, "getSQLhelper() called.");
		return mSQLhelper;
	}
	
	/**
	 * Gets the GeometryFactory which handles creation of
	 * geometries from coordinates using a specified precision
	 * model.
	 * @return The GeometryFactory.
	 */
	public GeometryFactory getGeometryFactory()  {
//		Log.d(TAG, "getGeometryFactory() called.");
		return mGeomFac;
	}
	
	/**
	 * Deactivates all layers by removing the "active" factor from
	 * the layer mode.
	 */
	public void deactivateLayers() {
		Log.d(TAG, "deactivateLayers() called.");
    	Cursor activeLayers = getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_LAYER, LocalSQLDBhelper.KEY_LAYER_COLUMNS, LocalSQLDBhelper.ALL_RECORDS, null, true);
		getActiveActivity().startManagingCursor(activeLayers);
		
		while(activeLayers.moveToNext()) { // As long as there's another row:
			if(activeLayers.getInt(2) % LocalSQLDBhelper.LAYER_MODE_ACTIVE == 0) {// If the layer is set to "active", then deactivate;
				getSQLhelper().updateData(LocalSQLDBhelper.TABLE_LAYER, activeLayers.getLong(0), LocalSQLDBhelper.KEY_LAYER_ID, new String[] {LocalSQLDBhelper.KEY_LAYER_USEMODE }, new String[] {String.valueOf(activeLayers.getInt(2) / LocalSQLDBhelper.LAYER_MODE_ACTIVE)});
			}
		}
    }
	
	/**
	 * Remove all layers that are not active or stored from the layer table,
	 * e.g. because they can no longer be accessed.
	 */
	public void clearRemoteLayers() {
		Log.d(TAG, "clearRemoteLayers() called.");
		getSQLhelper().deleteData(LocalSQLDBhelper.TABLE_LAYER, LocalSQLDBhelper.KEY_LAYER_ID, LocalSQLDBhelper.ALL_RECORDS,
				LocalSQLDBhelper.KEY_LAYER_USEMODE + " % " + LocalSQLDBhelper.LAYER_MODE_STORE + "<> 0" + " AND " + LocalSQLDBhelper.KEY_LAYER_USEMODE + " % " + LocalSQLDBhelper.LAYER_MODE_ACTIVE + "<> 0");
	}
	
	/**
	 * A method that checks which Activity is currently active and updates
	 * its layout according to the current state of the server connection,
	 * the active layer, and the map.
	 */
	public void updateLayoutOnState() {
		/* Record which is the active Activity as an int flag (for the switch). */
		String activity = getActiveActivity().getLocalClassName();
		Log.v(TAG, "updateLayoutOnState() run for: " + activity);
		int activityFlag = 	(activity.equalsIgnoreCase("ServerEditor"))		?	ACTIVITY_SERVEREDITOR :
							(activity.equalsIgnoreCase("ServerViewer"))		?	ACTIVITY_SERVERVIEWER :
							(activity.equalsIgnoreCase("LayerViewer"))		?	ACTIVITY_LAYERVIEWER :
							(activity.equalsIgnoreCase("MapViewer"))		?	ACTIVITY_MAPVIEWER :
							(activity.equalsIgnoreCase("AttributeEditor"))	?	ACTIVITY_ATTRIBUTEEDITOR :
																				ACTIVITY_DEFAULT;
		switch(activityFlag) {
			case ACTIVITY_SERVEREDITOR: {
				ServerEditor srvEditor = (ServerEditor) getActiveActivity();
				srvEditor.populateFields();
				if(getActiveServer() != null && srvEditor.getRowId() == getActiveServer().getID()) // If there is an active server, and the ServerEditor is viewing it.
						srvEditor.setConnectButtonState(CONNECTED);
				else if(srvEditor.getRowId() == getConnectingRow()) // If the ServerEditor is viewing a non-active server, that is currently being connected to.
					srvEditor.setConnectButtonState(CONNECTING);
				else // If there is no connection either present or underway related to this server.
					srvEditor.setConnectButtonState(DISCONNECTED);				
				break;
			}
			case ACTIVITY_SERVERVIEWER: {
				ServerViewer srvViewer = (ServerViewer) getActiveActivity();
				srvViewer.setLayout_ActiveServer(getActiveServer()); // Show the currently active ServerConnection name in green.
				break;
			}
			case ACTIVITY_LAYERVIEWER: {
				LayerViewer lv = (LayerViewer) getActiveActivity();
				lv.setLayout_ActiveLayer(getActiveLayer());
				lv.populateLayerList();
				lv.setLayout_EnableUploadButton(!getUploading());
				/*
		         * By sending the code in "action" to the runOnUiThread() method from a separate thread,
		         * its code will be placed in the UI Thread Message queue and thus happen after other
		         * queued messages (such as displaying the layout).
		         */
				new Thread(new Runnable() {
		        	public void run() {
		        		Runnable action = new Runnable() {
		        			public void run() {
		        				/* The following is put on the Message queue. */
		        				((LayerViewer) getActiveActivity()).setLayout_ListItems();
		        			}
		        		};
		        		((LayerViewer) getActiveActivity()).runOnUiThread(action);        		
		        	}
		        }).start();
				break;
			}
			case ACTIVITY_MAPVIEWER: {
				MapViewer mapViewer = (MapViewer) getActiveActivity();
				if(getActiveGetMap() != null) {
					if(getActiveGetMap().getImage() == null)
						Log.w(TAG, "No image stored by the active GetMap request.");
					mapViewer.showMapImage(getActiveGetMap().getImage()); // Display map image.
				}
					
				break;
			}
			case ACTIVITY_ATTRIBUTEEDITOR: {
				// Nothing to update.
				break;
			}
			default:
				break;
		}
	}
	
	/**
	 * Method that tries to make a GetCapabilities request to the most recently
	 * used server connection if there is one.
	 * @param allow Whether or not to allow the renew. Used with a flag to prevent multiple calls on application start-up.
	 */
	public void renewLastSrvConnection(boolean allow) {
		Log.d(TAG, "renewLastSrvConnection() called. First time: " + String.valueOf(allow));
		if(allow) {
			mInitialRenewSrvConnection = false; // Flag the action so it will not be executed again.
			
			/* Fetch the information on the last server connection used. */
			Cursor lastSrv = mSQLhelper.fetchData(LocalSQLDBhelper.TABLE_SRV, LocalSQLDBhelper.KEY_SRV_COLUMNS, LocalSQLDBhelper.RECENT_RECORD, null, false);
			getActiveActivity().startManagingCursor(lastSrv);
		
			/* If a valid server connection record was found, make a new GetCapabilities request which, if successful, sets a new active server. */
			if(lastSrv.moveToFirst() == false || lastSrv.getString(1).contentEquals(this.getString(R.string.srvedit_content_nolastuse))) {
		     	setActiveServer(null);
		       	Log.i(TAG, "No active server connection could be renewed.");
			}
			else
				makeGetCapabilitiesRequest(new ServerConnection(lastSrv.getLong(0), Utilities.DATE_LONG.format(new Date()), lastSrv.getString(2), lastSrv.getString(3), lastSrv.getString(4), lastSrv.getString(5), lastSrv.getString(6)));
		}
	}
	
	/**
	 * Try to load a Layer from the stored information
	 * in the local SQLite database and on the external
	 * storage of the device.
	 */
	public void renewActiveLayer() {
		Log.d(TAG, "renewActiveLayer() called. First time: " + String.valueOf(mInitialRenewLayer));
		if(mInitialRenewLayer) {
			mInitialRenewLayer = false; // Flag the action so it will not be executed again.
			
			/* Find the active layer if there is one and load it. */
			Cursor layerCursor = getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_LAYER, LocalSQLDBhelper.KEY_LAYER_COLUMNS, LocalSQLDBhelper.ALL_RECORDS, LocalSQLDBhelper.KEY_LAYER_USEMODE + " % " + LocalSQLDBhelper.LAYER_MODE_ACTIVE + " = 0", true);
			getActiveActivity().startManagingCursor(layerCursor);
			if(layerCursor.moveToFirst()) { // If there is an active layer:
				if(layerCursor.getInt(2) % LocalSQLDBhelper.LAYER_MODE_STORE == 0) { // If the layer is stored on the device; load it locally.
					Log.i(TAG, "Loading active layer from local storage...");
					setActiveLayer(generateGeographyLayer(layerCursor.getString(1)));
					makeLoadOperation(layerCursor.getInt(0));
				}
				else {// Else, try to load it from the geospatial server.
					Log.i(TAG, "Loading active layer from geospatial server...");
					makeDescribeFeatureTypeRequest(getActiveServer(), layerCursor.getString(1) , layerCursor.getInt(0));
				}
			}
			else
				Log.i(TAG, "No active layer found.");
		}
	}
	
	/**
	 * Method that initiates a GetCapabilities request on a separate thread.
	 * @param srv The ServerConnection to use for the request.
	 */
	public void makeGetCapabilitiesRequest(ServerConnection srv) {
		Log.d(TAG, "makeGetCapabilitiesRequest(ServerConnection=" + srv.getName() + ") called.");
		/* Record the new connect state and which server is being connected to. */
		setConnectState(CONNECTING);
		setConnectingRow(srv.getID());
		updateLayoutOnState();
		/*
		 * Start a separate Thread in which a getCapabilities request is created
		 * and the input ServerConnection is set as the new ActiveConnection if the
		 * server responds. If there is already a GetCapabilities thread running,
		 * kill it before starting the new one.
		 */
		if(getActiveCapabilities() != null)
			getActiveCapabilities().cancel(true);
		setActiveCapabilities(new GetCapabilities(this));
		getActiveCapabilities().execute(srv);
	}
	
	/**
	 * Method that initiates a GetMap request on a separate thread.
	 * @param srv The ServerConnection to use for the request.
	 * @param layout A layout with the same dimensions as the view to fill with the image returned by the GetMap request.
	 * @param bounds The LatLngBounds bounding box to use for the request, specifying what area the map should cover.
	 */
	public void makeGetMapRequest(ServerConnection srv, View layout, LatLngBounds bounds) {		
		Log.d(TAG, "makeGetMapRequest(ServerConnection=" + srv.getName() + ", View, BBox) called.");
		/*
		 * Start a separate Thread in which a getMap request is created
		 * and the input ServerConnection is set as the new ActiveConnection if the server responds.
		 */
		setActiveGetMap(new GetMap(this, layout.getWidth(), layout.getHeight(), bounds));
		getActiveGetMap().execute(srv);
	}
	
	/**
	 * Method that initiates a DescribeFeatureType request on a separate thread.
	 * @param srv The ServerConnection to use for the request.
	 * @param layerName The layer whose attribute information to request.
	 * @param rowId The row ID of the layer in the local SQLite database.
	 */
	public void makeDescribeFeatureTypeRequest(ServerConnection srv, String layerName, long rowId) {
		Log.d(TAG, "makeDescribeFeatureTypeRequest(ServerConnection=" + srv.getName() + ", layerName=" + layerName + ", rowId=" + String.valueOf(rowId) + ") called.");
		mDescribeFeatureType = new DescribeFeatureType(this, layerName, rowId);
		mDescribeFeatureType.execute(srv);
	}
	
	/**
	 * Starts the process of storing the active layer's data on
	 * a separate thread, on the geospatial server or locally.
	 * @param rwMode Operation mode. "Upload", "Write" or "Overwrite".
	 */
	public void makeSaveOperation(int rwMode) {
		Log.d(TAG, "makeSaveOperation(rwMode=" + rwMode + ") called.");
		mGeoHelper = new GeoHelper(this, rwMode);
		mGeoHelper.execute(getActiveLayer());
	}
	
	/**
	 * Starts the process of loading a locally stored layer into
	 * the active layer on a separate thread.
	 * @param rowId The rowId in the Layer table of the layer to activate.
	 */
	public void makeLoadOperation(long rowId) {
		Log.d(TAG, "makeLoadOperation(rowId=" + String.valueOf(rowId) + ") called.");
		mGeoHelper = new GeoHelper(this, GeoHelper.RWMODE_READ, rowId);
		mGeoHelper.execute(getActiveLayer());
	}
	
	/**
	 * Generates a GeographyLayer object with attributes, ready for
	 * adding geometry.
	 * 
	 * @param layerName The name of the layer to generate.
	 * @return A geometry-less GeographyLayer with its attributes loaded.
	 */
	public GeographyLayer generateGeographyLayer(String layerName) {
		Log.d(TAG, "generateGeographyLayer(layerName=" + layerName + ") called.");
		GeographyLayer layer = null;
		
		/* Find out the type of the layer. */
		Cursor geomTypeCursor = getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_FIELD_PREFIX + Utilities.dropColons(layerName, Utilities.RETURN_LAST), new String[] {LocalSQLDBhelper.KEY_FIELD_NAME, LocalSQLDBhelper.KEY_FIELD_DATATYPE }, LocalSQLDBhelper.ALL_RECORDS, LocalSQLDBhelper.KEY_FIELD_NAME + " LIKE('%_geom')", true);
		getActiveActivity().startManagingCursor(geomTypeCursor);
		if(geomTypeCursor.moveToFirst()) {
			if(GeographyLayer.getGeometryType(geomTypeCursor.getString(1)) != GeographyLayer.TYPE_INVALID)
				layer = new GeographyLayer(this, layerName, geomTypeCursor.getString(1));
			else {
				Log.e(TAG, "Invalid geometry type.");
				return null;
			}
		}
		else {
			Log.e(TAG, "No '_geom' field found.");
			return null;
		}
		
		/* Load the layer's fields. */
		Cursor fieldCursor = getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_FIELD_PREFIX + Utilities.dropColons(layerName, Utilities.RETURN_LAST), LocalSQLDBhelper.KEY_FIELD_COLUMNS, LocalSQLDBhelper.ALL_RECORDS, null, true);
		getActiveActivity().startManagingCursor(fieldCursor);
		if(fieldCursor.moveToFirst()) {			
			layer.addField(fieldCursor.getString(1), Boolean.parseBoolean(fieldCursor.getString(2)), fieldCursor.getString(3));
			while(fieldCursor.moveToNext())
				layer.addField(fieldCursor.getString(1), Boolean.parseBoolean(fieldCursor.getString(2)), fieldCursor.getString(3));
		}
		else
			Log.i(TAG, "No fields found for this layer.");		
		
		return layer;
	}
	
	/**
     * Method that displays an alert dialog to the user showing the string argument as the message text.
     * @param message Message to display in the alert dialog.
     * @param target The activity to display the AlertDialog in, pass null to default to the "active" Activity.
     */
    public void showAlertDialog(String message, Activity target) {
    	Log.d(TAG, "showAlertDialog(String) called.");
    	final String msg = message;
    	final Activity tg = target;
    	/*
         * By sending the code in "action" to the runOnUiThread() method from a separate thread,
         * its code will be placed in the UI Thread Message queue and thus happen after other
         * queued messages (such as displaying the layout).
         */
        new Thread(new Runnable() {
        	public void run() {
        		Runnable action = new Runnable() {
        			public void run() {
        				/* The following is put on the Message queue. */
        				AlertDialog alertDialog = new AlertDialog.Builder((tg == null) ? getActiveActivity() : tg).create();
        				alertDialog.setMessage(msg);
    	
        				/* Add a button to the dialog and set its text and button listener. */
        				alertDialog.setButton(alertDialog.getContext().getString(R.string.service_alert_buttontext_ok), new DialogInterface.OnClickListener() {
        					public void onClick(DialogInterface dialog, int which) {
        						dialog.dismiss();    		      
        					}
        				});		
        				alertDialog.show(); // Display the dialog to the user.
        			}
        		};
        		((tg == null) ? getActiveActivity() : tg).runOnUiThread(action);        		
        	}
        }).start();
    	
    }
    
    /**
     * Method that displays an on screen text (Toast) to the user showing
     * the string argument as the message text.
     * @param message Message to display in the Toast.
     * @param duration The duration of the message.
     */
    public void showToast(String message, int duration) {
    	Log.d(TAG, "showToast(String, duration(s)=" + duration + ") called.");
    	final String msg = message;
    	final int dur = duration;
    	
    	/* Show the Toast on the UI thread. */
    	Runnable action = new Runnable() {
			public void run() {
				Toast.makeText(getActiveActivity(), msg, dur).show();
			}
		};
		getActiveActivity().runOnUiThread(action);
    }
    
    /**
     * A local implementation of Binder that simply lets the calling
     * Activity fetch the Service reference.
     * 
     * @author Mattias Spångmyr
     * @version 0.10, 2013-07-16
     */
    public class SvcAccessor extends Binder {
    	/* The error tag for this Binder. */
//    	private String TAG = "BackboneSvc.SvcAccessor";
    	/** A reference to the application's background Service, received in the constructor. */
    	public final BackboneSvc mService;
    	
    	/**
    	 * Default constructor setting the reference to the BackboneSvc Service.
    	 * @param svc The BackboneSvc Service to store a reference to.
    	 */
        public SvcAccessor(BackboneSvc svc){
//        	Log.d(TAG, "SvcAccessor(BackboneSvc) constructor called.");
            mService = svc;
        }
    }
}

	