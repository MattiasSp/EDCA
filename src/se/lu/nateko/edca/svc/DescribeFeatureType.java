package se.lu.nateko.edca.svc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

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
import se.lu.nateko.edca.Utilities;
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
 * A subclass of AsyncTask for making a DescribeFeatureType request to a	*
 * geospatial server on a background worker thread and storing the result	*
 * in the local SQLite database.											*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.30, 2013-08-05												*
 * 																			*
 ****************************************************************************/
public class DescribeFeatureType extends AsyncTask<ServerConnection, Void, DescribeFeatureType> {
	/** The error tag for this ASyncTask. */
	public static final String TAG = "DescribeFeatureType";
	
	/** Constant defining the wait time before the DescribeFeatureType request times out. */
	public static final int TIME_OUT = 30;
	
	/** A reference to the application's background Service, received in the constructor. */
	private BackboneSvc mService;
	/** The HttpClient to use for sending the request. */
	private HttpClient mHttpClient;
	/** The server http address and request. */
	private URI mServerURI;
	/** Whether or not this request has received a response. */
	private boolean mHasResponse = false;
	/** The row id in the local SQLite database of the layer for which the request is being made. */
	private long mLayerRowId;
	/** The name of the layer for which the request is being made. */
	private String mLayerName;
	
	/**
	 * Constructor that stores a reference to the BackboneSvc creating this object and
	 * the name of the layer to request more information on.
	 * @param service The BackboneSvc whose reference to keep.
	 * @param layerName The name of the layer targeted by the DescribeFeatureType request.
	 * @param rowId The row ID of the layer in the local SQLite database.
	 */
	public DescribeFeatureType(BackboneSvc service, String layerName, long rowId) {
		Log.d(TAG, "DescribeFeatureType(BackboneSvc, String, long) called.");
		mService = service;
		mLayerRowId = rowId;
		mLayerName = layerName;
	}
	
	/**
	 * Method run in a separate worker thread when GetCapabilities.execute() is called.
	 * Takes the server info from the ServerConnection supplied and stores it as a URI.
	 * @param srvs An array of ServerConnection objects from which to form the URI. May only contain 1.
	 */
	@Override
	protected DescribeFeatureType doInBackground(ServerConnection... srvs) {
		Log.d(TAG, "doInBackground(ServerConnection...) called.");
		
		/* Try to form an URI from the supplied ServerConnection info. */
		if(srvs[0] == null) // Cannot connect unless there is an active connection.
			return this;
		String uriString = srvs[0].getAddress()
					+ "/wfs?service=wfs&version=1.1.0&request=DescribeFeatureType&typeName="+mLayerName;
		try {
			mServerURI = new URI(uriString);
		} catch (URISyntaxException e) {
			Log.e(TAG, e.getMessage() + ": " + uriString);
		}
		
		/* If there is already a layer stored with this name; clear its data to make room for the new DescribeFeatureType result. */
		mService.getSQLhelper().getSQLiteDB().execSQL("DROP TABLE IF EXISTS " + LocalSQLDBhelper.TABLE_FIELD_PREFIX + Utilities.dropColons(mLayerName, Utilities.RETURN_LAST));
		mService.getSQLhelper().createFieldTable(mLayerName);
		GeoHelper.deleteGeographyLayer(mLayerName);
		
		try	{ // Get or wait for exclusive access to the HttpClient.
			mHttpClient = mService.getHttpClient(); 
		} catch(InterruptedException e) { Log.w(TAG, "Thread " + Thread.currentThread().getId() + ": " + e.toString()); }
		
		mHasResponse = describeFeatureTypeRequest(); // Make the DescribeFeatureType request to the server and record the success state.
		mService.unlockHttpClient(); // Release the lock on the HttpClient, allowing new connections to be made.
		
		Log.v(TAG, "DescribeFeatureType request succeeded: " + String.valueOf(mHasResponse));
		if(mHasResponse) {
			/* Update the database and set a new active layer. */
			mService.setActiveLayer(mService.generateGeographyLayer(mLayerName));
			mService.deactivateLayers();
			mService.getSQLhelper().updateData(LocalSQLDBhelper.TABLE_LAYER, mLayerRowId, LocalSQLDBhelper.KEY_LAYER_ID, new String[] {LocalSQLDBhelper.KEY_LAYER_USEMODE}, new String[] {String.valueOf(LocalSQLDBhelper.LAYER_MODE_ACTIVE * LocalSQLDBhelper.LAYER_MODE_DISPLAY)});			
		}
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
	protected void onPostExecute(DescribeFeatureType result) {
		Log.d(TAG, "onPostExecute(DescribeFeatureType) called.");

		mService.stopAnimation(); // Stop the animation, showing that a web communicating thread is no longer active.
		
		if(!mHasResponse)
			mService.clearConnection(true); // Report a failed connection attempt.
		else
			mService.setConnectState(BackboneSvc.CONNECTED, mService.getConnectingRow()); // Set the state to Connected, to the row that was being connected to.
		
		mService.updateLayoutOnState();

		super.onPostExecute(result);
	}

	/**
	 * Method that calls a geospatial server using a DescribeFeatureType request and,
	 * if successful, forwards the resulting XML object to the XML parser.
	 * @return Returns true if successful, otherwise false.
	 */
	protected boolean describeFeatureTypeRequest() {
//		Log.d(TAG, "describeFeatureTypeRequest() called.");
		
	    /* Execute the HTTP request. */
		HttpGet httpGetMethod = new HttpGet(mServerURI);	    
	    HttpResponse response;
		try {
			final HttpParams httpParameters = mHttpClient.getParams();
		    HttpConnectionParams.setConnectionTimeout(httpParameters, TIME_OUT * 1000);
		    HttpConnectionParams.setSoTimeout        (httpParameters, TIME_OUT * 1000);
			
		    response = mHttpClient.execute(httpGetMethod);
		    Log.v(TAG, "DescribeFeatureType request made to database: " + httpGetMethod.getURI().toString());
		    
		    InputStream xmlStream = response.getEntity().getContent();
		    InputStreamReader reader = new InputStreamReader(xmlStream, "UTF-8");
		    BufferedReader buffReader = new BufferedReader(reader, 2048);
		    
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
	 * Parses an XML response from a DescribeFeatureType request and stores the layer
	 * attribute names and their data types in a GeographyLayer object.
	 * @param xmlResponse A reader wrapped around an InputStream containing the XML response from a DescribeFeatureType request.
	 */
	protected boolean parseXMLResponse(Reader xmlResponse) {
//		Log.d(TAG, "parseXMLResponse(Reader) called.");
		
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
	 * @version 0.11, 2012-07-20
	 */
	private class XMLEventHandler extends DefaultHandler {
		/** The error tag for this XMLEventHandler. */
		public static final String TAG = "DescribeFeatureType.XMLEventHandler";
		
		/** Whether or not the parser is within a <SEQUENCE></SEQUENCE> block. */
		private boolean mWithinSequence = false;
		/** Flag showing the type of element currently being parsed. */
		private int mElementType = 0;
		/** The name of an attribute currently being parsed. */
		private String mAttributeName = "";
		/** Whether or not a currently parsed attribute is allowed to be null. */
		private boolean mAttributeNillable = true;
		/** The attribute type of an attribute currently being parsed. */
		private String mAttributeType = "";
		
		/** Constant defining the name of a "SEQUENCE" element. */
		private static final String ELEMENT_KEY_SEQUENCE = "sequence";
		/** Constant defining the name of an "ELEMENT" element. */
		private static final String ELEMENT_KEY_ELEMENT = "element";
		/** Constant identifying a "SEQUENCE" element. */
		private static final int ELEMENT_MAPPING_SEQUENCE = 1;
		/** Constant identifying an "ELEMENT" element. */
		private static final int ELEMENT_MAPPING_ELEMENT = 2;
		/** Constant identifying other elements than "SEQUENCE" or "ELEMENT". */
		private static final int ELEMENT_MAPPING_DEFAULT = 0;
		
		@Override
		public void startDocument() throws SAXException {
			Log.d(TAG, "startDocument() called: Examining layer attributes...");
			super.startDocument();
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
//			Log.d(TAG, "startElement(String, String, String, Attributes) called.");
			/* Record relevant element names as integer mappings.
			 *	xsd:sequence	= 1
			 *	xsd:element		= 2
			 */
			mElementType = (localName.equalsIgnoreCase(ELEMENT_KEY_SEQUENCE))	?	ELEMENT_MAPPING_SEQUENCE	:	// Map xsd:sequence as 1.
							(localName.equalsIgnoreCase(ELEMENT_KEY_ELEMENT)) 	?	ELEMENT_MAPPING_ELEMENT		:	// Map xsd:element as 2.
																					ELEMENT_MAPPING_DEFAULT;		// Map any other as 0.
			
			switch(mElementType) {
				case ELEMENT_MAPPING_SEQUENCE: {
					mWithinSequence = true;
					break;
				}
				case ELEMENT_MAPPING_ELEMENT: {
					if(mWithinSequence) {
						try {
							mAttributeName = attributes.getValue("", "name");
							mAttributeNillable = (attributes.getValue("", "nillable").equalsIgnoreCase("true")) ? true : false;
							mAttributeType = attributes.getValue("", "type");
//							Log.v(TAG, "Element attributes: " + attributes.getQName(1));
						} catch (NullPointerException e) { Log.w(TAG, "No such element attribute."); }
					}					
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
			// Not looking for character data.			
			super.characters(ch, start, length);
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
//			Log.d(TAG, "endElement(String, String, String) called.");
			switch(mElementType) {
				case ELEMENT_MAPPING_SEQUENCE: { // Record that a xsd:sequence element has ended.
					mWithinSequence = false;
					mElementType = ELEMENT_MAPPING_DEFAULT;
					break;
				}
				case ELEMENT_MAPPING_ELEMENT: { // Log the info and store it in the local SQLite database.
					if(mWithinSequence) {						
						Log.v(TAG, "Attribute: " + mAttributeName + ", Nullable: " + String.valueOf(mAttributeNillable) + ", Data type: " + mAttributeType + ".");
						mService.getSQLhelper().insertData(LocalSQLDBhelper.TABLE_FIELD_PREFIX + Utilities.dropColons(mLayerName, Utilities.RETURN_LAST), new String[] {LocalSQLDBhelper.KEY_FIELD_NAME, LocalSQLDBhelper.KEY_FIELD_NULLABLE, LocalSQLDBhelper.KEY_FIELD_DATATYPE}, new String[] {mAttributeName, String.valueOf(mAttributeNillable), mAttributeType});
						mElementType = ELEMENT_MAPPING_SEQUENCE;
					}
					else
						mElementType = ELEMENT_MAPPING_DEFAULT;
					
					break;
				}
				default:
					break;
			}
			super.endElement(uri, localName, qName);
		}		

		@Override
		public void endDocument() throws SAXException {
//			Log.d(TAG, "endDocument() called: Finished examining layer attributes.");
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
