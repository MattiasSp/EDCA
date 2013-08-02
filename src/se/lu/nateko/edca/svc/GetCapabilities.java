package se.lu.nateko.edca.svc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import se.lu.nateko.edca.BackboneSvc;
import se.lu.nateko.edca.R;
import se.lu.nateko.edca.ServerEditor;
import android.database.Cursor;
import android.os.AsyncTask;
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
 * A subclass of AsyncTask for making a GetCapabilities request to a		*
 * geospatial server on a background worker thread and publishing the		*
 * result, including what layers are available on the server, back to the	*
 * UI thread.																*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.46, 2013-08-01												*
 * 																			*
 ****************************************************************************/
public class GetCapabilities extends AsyncTask<ServerConnection, Void, GetCapabilities> {
	/** The error tag for this ASyncTask. */
	public static final String TAG = "GetCapabilities";
	/** Constant defining the wait time before the GetCapabilities request times out. */
	private static final int TIME_OUT = 15;
	/** The server http address and request. */
	private URI mServerURI;
	/** The ServerConnection that the request is being made to. */
	private ServerConnection mServerConnection;
	/** Whether or not this request has received a response. */
	private boolean mHasResponse;
	/** Whether or not the response include a valid Capabilities element. */
	private boolean mHasCapabilitiesElements = false;
	
	/** A reference to the application's background Service, received in the constructor. */
	private BackboneSvc mService;
	/** The HttpClient to use for sending the request. */
	private HttpClient mHttpClient;
	/** ArrayList of the names of already stored (in the local SQLite database) layers. Checked when adding new layers in order to avoid duplicates. */
	private ArrayList<String> mStoredLayers = new ArrayList<String>();
	
	/**
	 * Constructor that stores a reference to the BackboneSvc creating this object.
	 * @param service The BackboneSvc whose reference to keep.
	 */
	public GetCapabilities(BackboneSvc service) {
		Log.d(TAG, "GetCapabilities(BackboneSvc) called.");
		mService = service;
	}
	
	/**
	 * Method run in a separate worker thread when GetCapabilities.execute() is called.
	 * Takes the server info from the ServerConnection supplied and stores it as a URI.
	 * @param srvs	An array of ServerConnection objects from which to form the URI. May only contain 1.
	 */
	@Override
	protected GetCapabilities doInBackground(ServerConnection... srvs) {
		Log.d(TAG, "doInBackground(ServerConnection...) called.");
		
		mServerConnection = srvs[0]; // Stores the ServerConnection info.
		
		/* Try to form an URI from the supplied ServerConnection info. */
		String uriString = (mServerConnection.getMode() == ServerEditor.SIMPLE_ADDRESS_MODE ?
				mServerConnection.getSimpleAddress()
				: "http://" + srvs[0].toString())
					+ "/wms?service=wms&version=1.1.0&request=GetCapabilities";
		try {
			mServerURI = new URI(uriString);
		} catch (URISyntaxException e) {
			Log.e(TAG, e.getMessage() + ": " + uriString);
		}
		
		try	{ // Get or wait for exclusive access to the HttpClient.
			mHttpClient = mService.getHttpClient(); 
		} catch(InterruptedException e) { Log.e(TAG, "Thread " + Thread.currentThread().getId() + ": " + e.toString()); }
		
		mHasResponse = getCapabilitiesRequest(); // Make the GetCapabilities request to the server and record the success state.
		mService.unlockHttpClient(); // Release the lock on the HttpClient, allowing new connections to be made.
		Log.v(TAG, "GetCapabilities request succeeded: " + String.valueOf(mHasResponse));
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
	protected void onPostExecute(GetCapabilities result) {
		Log.d(TAG, "onPostExecute(GetCapabilities) called.");
		
		mService.stopAnimation(); // Stop the animation, showing that a web communicating thread is no longer active.
		
		if(result.mHasResponse) { // Connection succeeded.
			/* Update the database. */
			mService.setActiveServer(mServerConnection);
			mService.getSQLhelper().updateData(LocalSQLDBhelper.TABLE_SRV, mServerConnection.getID(), LocalSQLDBhelper.KEY_SRV_ID, new String[] {LocalSQLDBhelper.KEY_SRV_LASTUSE}, new String[] {mServerConnection.getLastUse()});
			mService.setConnectState(BackboneSvc.CONNECTED, mService.getConnectingRow()); // Set the state to Connected, to the row that was being connected to.
		}
		else if(mService.getActiveServer() == null || mService.getActiveServer().getID() == mService.getConnectingRow()) // Connection failed with no connection present or while connecting to the currently active server.
			mService.clearConnection(true); // Report the failed connection attempt.
		else { // Connection failed while connecting to a new server, keep old connection.
			mService.setConnectState(BackboneSvc.CONNECTED, mService.getActiveServer().getID());
			mService.showAlertDialog(mService.getString(R.string.service_connectionfailed), null);
		}
		mService.updateLayoutOnState(); // Update the layout to reflect the results of the GetCapabilities request.

		mService.renewActiveLayer(mService.mInitialRenewLayer);
		
		super.onPostExecute(result);
	}
	
	/**
	 * Method that calls a geospatial server using a GetCapablities request and,
	 * if successful, forwards the resulting XML object to the XML parser.
	 * @return	Returns true if successful, otherwise false.
	 */
	protected boolean getCapabilitiesRequest() {
		Log.d(TAG, "getCapabilitiesRequest() called.");
		
	    /* Execute the HTTP request. */
		HttpGet httpGetMethod = new HttpGet(mServerURI);	    
	    HttpResponse response;
		try {
			final HttpParams httpParameters = mHttpClient.getParams();
		    HttpConnectionParams.setConnectionTimeout(httpParameters, TIME_OUT * 1000);
		    HttpConnectionParams.setSoTimeout        (httpParameters, TIME_OUT * 1000);
		    
		    response = mHttpClient.execute(httpGetMethod);
		    Log.i(TAG, "GetCapabilities request made to server: " + httpGetMethod.getURI().toString());
			
		    InputStream xmlStream = response.getEntity().getContent();
		    InputStreamReader reader = new InputStreamReader(xmlStream, "UTF-8");
		    BufferedReader buffReader = new BufferedReader(reader, 8192);
		    
		    /* Remove all non-stored and inactive layers from the table to make room for the new result of the GetCapabilities XML parsing. */
		    mService.clearRemoteLayers();
		    
		    /* Check for already stored layers so they are not duplicated. */
		    Cursor storedCursor = mService.getSQLhelper().fetchData(LocalSQLDBhelper.TABLE_LAYER, LocalSQLDBhelper.KEY_LAYER_COLUMNS, LocalSQLDBhelper.ALL_RECORDS, null, true);
		    mService.getActiveActivity().startManagingCursor(storedCursor);
		    while(storedCursor.moveToNext()) {
		    	mStoredLayers.add(storedCursor.getString(1));
		    	Log.v(TAG, "Stored layer: " + storedCursor.getString(1));
		    }
		    
		    try {
		    	Log.v(TAG, "Sending response (BufferedReader) to parser...");
		    	return parseXMLResponse(buffReader); // Send the HttpResponse as a Reader to parse its content.
		    } finally {
		    	buffReader.close();
		    }
			    
		} catch (MalformedURLException e) {
			Log.e(TAG, e.toString());
			return false;
		} catch (IOException e) {
			Log.e(TAG, e.toString());
			return false;
		}
	}
	
	/**
	 * Parses an XML response from a GetCapabilities request and stores available Layers
	 * and options in the local SQLite database.
	 * @param xmlResponse A reader wrapped around an InputStream containing the XML response from a GetCapabilities request.
	 */
	protected boolean parseXMLResponse(Reader xmlResponse) {
		Log.d(TAG, "parseXMLResponse(Reader) called.");
		
		try {
			SAXParserFactory spfactory = SAXParserFactory.newInstance(); // Make a SAXParser factory.
			spfactory.setValidating(false); // Tell the factory not to make validating parsers.
			SAXParser saxParser = spfactory.newSAXParser(); // Use the factory to make a SAXParser.
			XMLReader xmlReader = saxParser.getXMLReader(); // Get an XML reader from the parser, which will send event calls to its specified event handler.
			XMLEventHandler xmlEventHandler = new XMLEventHandler();
			xmlReader.setContentHandler(xmlEventHandler); // Set which event handler to use.
			xmlReader.setErrorHandler(xmlEventHandler); // Also set where to send error calls.
			InputSource source = new InputSource(xmlResponse); // Make an InputSource from the XML input to give to the reader.
		    xmlReader.parse(source); // Start parsing the XML.
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			return false;
		}
		return true;
	}
	
	/**
	 * Event handler class that handles calls from an XML reader.
	 * @author Mattias Spångmyr
	 * @version 0.20, 2013-01-09
	 */
	private class XMLEventHandler extends DefaultHandler {
		/** The error tag for this XMLEventHandler. */
		public static final String TAG = "GetCapabilities.XMLEventHandler";
		
		/** Whether or not the parser is within a <LAYER></LAYER> block. */
		private boolean mWithinLayerParent = false;
		/** Whether or not the parser is within two (inside a nested) <LAYER></LAYER> block. */
		private boolean mWithinLayerChild = false;
		/** The number of the level where inner <LAYER></LAYER> blocks are located. */
		private int mLayerChildLevel = 0;
		/** The number of the level of the element that is currently being parsed. */
		private int mElementLevel = 0;
		/** The type of element that is currently being parsed. */
		private int mElementType = 0;
		/** Whether or not the currently parsed LAYER element is queryable. */
		private boolean mLayerChildQueryable = false;
		/** The value of the element just parsed. */
		private String mElementValue = "";
		
		/** Flag recording whether or not the currently examined layer name is already present
		 * in the layer table of the local SQLite database (first position) and whether or not
		 * any layer name has been present (second position). */
		private boolean[] mConflict = new boolean[] { false, false };
		
		/** Constant defining the name of a "layer" element. */
		private static final String ELEMENT_KEY_LAYER = "layer";
		/** Constant defining the name of a "name" element. */
		private static final String ELEMENT_KEY_NAME = "name";
		/** Constant defining the name of an "srs" element. */
		private static final String ELEMENT_KEY_SRS = "SRS";
		/** Constant defining the name of a "service" element. */
		private static final String ELEMENT_KEY_SERVICE = "service";
		
		/** Constant identifying a "layer" element. */
		private static final int ELEMENT_MAPPING_LAYER = 1;
		/** Constant identifying a "name" element. */
		private static final int ELEMENT_MAPPING_NAME = 2;
		/** Constant identifying an "srs" element. */
		private static final int ELEMENT_MAPPING_SRS = 3;
		/** Constant identifying a "service" element. */
		private static final int ELEMENT_MAPPING_SERVICE = 4;
		/** Constant identifying elements other than those with specific identifiers. */
		private static final int ELEMENT_MAPPING_DEFAULT = 0;
		
		// TODO Handle coordinate reference system.
		//private boolean mServiceHasWGS84 = false; 
		
		@Override
		public void startDocument() throws SAXException {
			Log.d(TAG, "startDocument() called: Examining server capabilities...");
			super.startDocument();
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
//			Log.d(TAG, "startElement(String, String, String, Attributes) called.");
			mElementLevel++; // New element entered: the level in increased by one.
			
			/* Record relevant element names as integer mappings. */
			mElementType = (localName.equalsIgnoreCase(ELEMENT_KEY_LAYER))	?	ELEMENT_MAPPING_LAYER	:	// Map LAYER as 1.
							(localName.equalsIgnoreCase(ELEMENT_KEY_NAME)) 	?	ELEMENT_MAPPING_NAME	:	// Map NAME as 2.
							(localName.equalsIgnoreCase(ELEMENT_KEY_SRS))	?	ELEMENT_MAPPING_SRS		:	// Map SRS as 3.
						(localName.equalsIgnoreCase(ELEMENT_KEY_SERVICE))	?	ELEMENT_MAPPING_SERVICE	:	// Map SERVICE as 4.
																				ELEMENT_MAPPING_DEFAULT;	// Map any other as 0.
			
			if(mElementType > 0) // Record if proper element has been found.
				mHasCapabilitiesElements = true;
			
			switch(mElementType) {
				case ELEMENT_MAPPING_LAYER: {
					if(mWithinLayerParent == false) { // If the element is LAYER, but the parent LAYER has not been read before.
						mWithinLayerParent = true; // Set the parent Layer to read.
					}
					else { // If the element is LAYER and the parent LAYER has been read.
						mLayerChildLevel = mElementLevel; // This is the level of child LAYERs.
						mWithinLayerChild = true;
						mLayerChildQueryable = (attributes.getValue("queryable").contentEquals("1")) ? true : false; // Record if the child LAYER is queryable.
					}
					break;
				}
				case ELEMENT_MAPPING_SERVICE: {
					// TODO
					break;
				}
				default:
					break;
			}			
			super.startElement(uri, localName, qName, attributes);
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
//			Log.d(TAG, "characters(char[], int, int) called.");
			/* Store the character data for relevant element types. */
			switch (mElementType) {
				case ELEMENT_MAPPING_DEFAULT:
					break;
				default: {					
					mElementValue = new String(ch, start, length); // Store the value of the layer element, including namespace.
					break;
				}
			}			
			super.characters(ch, start, length);
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
//			Log.d(TAG, "endElement(String, String, String) called.");
			switch(mElementType) {
				case ELEMENT_MAPPING_LAYER: { // Record that a LAYER element (Parent/Child) has ended.
					if(mWithinLayerChild)
						mWithinLayerChild = false;
					else
						mWithinLayerParent = false;
					break;
				}
				case ELEMENT_MAPPING_NAME: { // Log LAYER name and if queryable; store it in the local SQLite database.
					if(mElementLevel != (mLayerChildLevel + 1)) // If the element is not the child LAYER's NAME.
						break;
					if(mLayerChildQueryable) {
						for(int i=0; i < mStoredLayers.size(); i++) {
							if(mElementValue.equalsIgnoreCase(mStoredLayers.get(i))) { // Check for conflicts.
								mConflict[0] = true;
								mConflict[1] = true;
							}
						}
						
						if(mConflict[0]) { // There is a stored layer with the same name as this new one. Notify.							
							Log.i(TAG, "Layer not added: " + mElementValue);
							mConflict[0] = false; // Reset the conflict flag for the next layer check.
						}
						else {
							mService.getSQLhelper().insertData(LocalSQLDBhelper.TABLE_LAYER, new String[] {LocalSQLDBhelper.KEY_LAYER_NAME, LocalSQLDBhelper.KEY_LAYER_USEMODE}, new String[] {mElementValue, String.valueOf(LocalSQLDBhelper.LAYER_MODE_INACTIVE)});
							Log.i(TAG, "Layer added: " + mElementValue);
						}
					}
					else
						Log.w(TAG, "Non-queryable LAYER: " + mElementValue);
					break;
				}
				/*
				case 3: { // Record if the service has WGS 1984 as a coordinate system.
					if(!mWithinLayerChild)
						mServiceHasWGS84 = true;
				}
				*/
				default:
					break;
			}
			
			mElementLevel--; // An element was left, the level is decreased by one. 
			
			super.endElement(uri, localName, qName);
		}

		@Override
		public void endDocument() throws SAXException {
//			Log.d(TAG, "endDocument() called: Finished examining layer attributes.");
			if(mConflict[1]) // If any layer was not added because its name was already stored locally, notify the user.
				mService.showAlertDialog(mService.getString(R.string.service_layerconflict), null);
			if(!mHasCapabilitiesElements)
				throw new SAXException("No valid elements found!");
			super.endDocument();
		}

		
		
		@Override
		public void warning(SAXParseException e) throws SAXException {
			Log.w(TAG, "warning(SAXParseException) called: " + e.toString());
			super.warning(e);
		}

		@Override
		public void error(SAXParseException e) throws SAXException {
			Log.e(TAG, "error(SAXParseException) called: " + e.toString());
			super.error(e);
		}

		@Override
		public void fatalError(SAXParseException e) throws SAXException {
			Log.e(TAG, "fatalError(SAXParseException) called: " + e.toString());
			super.fatalError(e);
		}
		
		

	}
}
