package se.lu.nateko.edca.svc;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import se.lu.nateko.edca.BackboneSvc;
import se.lu.nateko.edca.Utilities;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.LatLngBounds;

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
 * A subclass of AsyncTask for making a GetMap request to a geospatial		*
 * server on a background worker thread and publishing the resulting map	*
 * image back to the UI thread.												*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.43, 2013-06-25												*
 * 																			*
 ****************************************************************************/
public class GetMap extends AsyncTask<ServerConnection, Void, GetMap> {
	/** The error tag for this ASyncTask. */
	public static final String TAG = "GetMap";
	/** Constant defining the wait time before the GetMap request times out. */
	private static final int TIME_OUT = 25;
	
	/** Constant identifying that the GetMap request failed. */
	public static final int RESULT_FAILURE = 0;
	/** Constant identifying that the GetMap request succeeded. */
	public static final int RESULT_SUCCESS = 1;
	/** Constant identifying that the GetMap request was run without layers to request. */
	public static final int RESULT_NOLAYERS = 2;
	
	/** The server http address and request. */
	private URI mServerURI;
	/** The ServerConnection that the request is being made to. */
	private ServerConnection mServerConnection;
	/** Whether or not this request has received a response. */
	private boolean mHasResponse;
	/** The Bitmap image received in response to the GetMap request. */
	private Bitmap mImage;
	
	/** A reference to the application's background Service, received in the constructor. */
	private BackboneSvc mService;
	/** The HttpClient to use for sending the request. */
	private HttpClient mHttpClient;
	
	/** The width of the layout to fill with the returned map image, in pixels. */
	private int mWidth;
	/** The height of the layout to fill with the returned map image, in pixels. */
	private int mHeight;
	/** The bounds used for this GetMap request, using the lower left and upper right corners as LatLng objects. */
	private LatLngBounds mBounds;
	
	/**
	 * Constructor that stores a reference to the BackboneSvc creating this object
	 * as well as the screen layout dimensions and the bounds for the map.
	 * @param service The BackboneSvc whose reference to keep.
	 * @param width The width of the layout to fill with the returned map image, in pixels.
	 * @param height The height of the layout to fill with the returned map image, in pixels.
	 * @param bounds The bounds used for this GetMap request, using the lower left and upper right corners as LatLng objects.
	 */
	public GetMap(BackboneSvc service, int width, int height, LatLngBounds bounds) {
//		Log.d(TAG, "GetMap(BackboneSvc, int, int, LatLngBounds) called.");
		mService = service;
		mWidth = width;
		mHeight = height;
		mBounds = bounds;
	}
	
	/**
	 * Method run in a separate worker thread when GetMap.execute() is called.
	 * Takes the server info from the ServerConnection supplied and stores it as a URI.
	 * @param srvs	An array of ServerConnection objects from which to form the URI. May only contain 1.
	 */
	@Override
	protected GetMap doInBackground(ServerConnection... srvs) {
//		Log.d(TAG, "doInBackground(ServerConnection...) called.");
		mService.startAnimation(); // Start the animation, showing that a web communicating thread is active.
		mServerConnection = srvs[0]; // Stores the ServerConnection info.
		
		/* Make the GetMap request to the server and record the success state. */
		try	{ // Get or wait for exclusive access to the HttpClient.
			mHttpClient = mService.getHttpClient(); 
		} catch(InterruptedException e) { Log.e(TAG, "Thread " + Thread.currentThread().getId() + ": " + e.toString()); }

		mHasResponse = (getMapRequest() == RESULT_FAILURE) ? false : true;
		mService.unlockHttpClient(); // Release the lock on the HttpClient, allowing new connections to be made.
		
		Log.v(TAG, "GetMap request succeeded: " + String.valueOf(mHasResponse));
		return this;
	}


	/**
	 * Method called from within the worker thread of doInBackground() to
	 * publish the progress to the UI thread.
	 */
	@Override
	protected void onProgressUpdate(Void... voids) {
		Log.d(TAG, "onProgressUpdate(Void...) called.");
		// No progress update.
		super.onProgressUpdate();
	}


	/**
	 * Method that receives the return from doInBackground() and uses it
	 * to perform work on the UI thread.
	 */
	@Override
	protected void onPostExecute(GetMap result) {
//		Log.d(TAG, "onPostExecute(GetMap) called.");
		mService.stopAnimation(); // Stop the animation, showing that a web communicating thread is no longer active.
		
		if(!result.mHasResponse)
			mService.clearConnection(true); // Report the failed connection.

		mService.updateLayoutOnState();
				
		super.onPostExecute(result);
	}
	
	/**
	 * Method that calls a geospatial server using a GetMap request and,
	 * if successful, stores the resulting InputStream in a reusable version.
	 * @return	Returns true if successful, otherwise false.
	 */
	protected int getMapRequest() {
//		Log.d(TAG, "getMapRequest() called");
		
		/* Check if any layers should be requested. If not, cancel the GetMap request. */
		String layers = fetchLayerNames();
		if(layers.contentEquals(""))
			return RESULT_NOLAYERS;
		
		/* Try to form an URI from the supplied ServerConnection info and the list of
		 * layers set to display in the local SQLite database. */
		String uriString = "";
		try {
			uriString = "http://" + mServerConnection.toString()
				+ "/wms?service=wms&version=1.1.0&request=GetMap&layers=" 
				+ layers
				+ "&bbox=" + Utilities.latLngBoundsToString(mBounds)
				+ "&styles="
				+ "&transparent=true"
				+ "&srs=epsg:3857"
				+ "&format=image/png"
				+ "&width=" + String.valueOf(mWidth)
				+ "&height=" + String.valueOf(mHeight);
			mServerURI = new URI(uriString);
		} catch (NullPointerException e) {
			Log.e(TAG, e.toString());
			return RESULT_FAILURE;
		} catch (URISyntaxException e) {
			Log.e(TAG, e.toString() + ": " + uriString);
			return RESULT_FAILURE;
		}

	    /* Execute the HTTP request. */
		HttpGet httpGetMethod = new HttpGet(mServerURI);
	    HttpResponse response;
		try {	
		    final HttpParams httpParameters = mHttpClient.getParams();
		    HttpConnectionParams.setConnectionTimeout(httpParameters, TIME_OUT * 1000);
		    HttpConnectionParams.setSoTimeout        (httpParameters, TIME_OUT * 1000);
			
		    response = mHttpClient.execute(httpGetMethod);
		    Log.i(TAG, "getMap request made to database: " + httpGetMethod.getURI().toString());
		    
		    InputStream imageStream = response.getEntity().getContent();		    
		    try {
//		    	Log.v(TAG, "Saving response as a Bitmap.");
		    	setImage(BitmapFactory.decodeStream(imageStream)); // Save the HttpResponse as a Bitmap image to be displayed.				
			} finally {
				imageStream.close();
			}
			
			return RESULT_SUCCESS;
			    
		} catch (MalformedURLException e) {
			Log.e(TAG, e.toString());
			return RESULT_FAILURE;
		} catch (IOException e) {
			Log.e(TAG, e.toString());
			return RESULT_FAILURE;
		}
	}
	
	/**
	 * Method that fetches the names of the layers set to be displayed in the local
	 * SQLite database and returns them in a single String, with the names separated
	 * by commas.
	 * @return A String with the names of the layers to be included in a GetMap request.
	 */
	public String fetchLayerNames() {
//		Log.d(TAG, "fetchLayerNames() called.");
		Cursor layers = mService.getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_LAYER, LocalSQLDBhelper.KEY_LAYER_COLUMNS, LocalSQLDBhelper.ALL_RECORDS, null, false);
		mService.getActiveActivity().startManagingCursor(layers);
		String layerString = "";
		if(layers.moveToFirst()) {
			int layerCount = 0;
			if(layers.getInt(2) % LocalSQLDBhelper.LAYER_MODE_DISPLAY == 0) { // If the layer is set to "display", then;
				layerString = layerString + layers.getString(1); // Add the first name to the String.
				layerCount++;
			}
			while(layers.moveToNext()) { // As long as there's another row:
				if(layers.getInt(2) % LocalSQLDBhelper.LAYER_MODE_DISPLAY == 0) {// If the layer is set to "display", then;
					if(layerCount > 0)
						layerString = layerString + ","; // Add a comma separator to the String.
					layerString = layerString + layers.getString(1); // Add the next name to the String.
					layerCount++;
				}
			}
			Log.v(TAG, String.valueOf(layerCount) + " layers are to be included in the GetMap request.");
		}
		Log.v(TAG, "layerString: " + layerString);
		return layerString;
	}

	/**
	 * Set method for the stored Bitmap response
	 * of a GetMap request.
	 * @param image A Bitmap image.
	 */
	public void setImage(Bitmap image) {
//		Log.d(TAG, "setImage(Bitmap) called.");
		mImage = image;
	}
	
	/**
	 * Get method for the stored Bitmap response
	 * of a GetMap request.
	 * @return An Bitmap image.
	 */
	public Bitmap getImage() {
//		Log.d(TAG, "getImage() called.");
		return mImage;
	}
}
