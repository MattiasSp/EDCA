package se.lu.nateko.edca.svc;

import java.net.URI;
import java.net.URISyntaxException;

import se.lu.nateko.edca.Utilities;
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
 * ServerConnection object class, instantiated and managed by a BackboneSvc	*
 * object. Carries all information about a geospatial server required to	*
 * connect to it as instance variables. Depending on the ServerConnection	*
 * mode, this can include either the full address (simple address mode) or	*
 * the IP and path in addition to the workspace name and port for the		*
 * server.																	*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.55, 2013-08-05												*
 * 																			*
 ****************************************************************************/
public class ServerConnection {
	/** The error tag for this class. */
	public static final String TAG = "ServerConnection";
	
	/** Constant identifying the simple address mode, where the entire server address is entered as a single input String. */
    public static final int SIMPLE_ADDRESS_MODE = 1;
    /** Constant identifying the exploded address mode, where the server address has to be entered as separate parts. */
    public static final int IP_ADDRESS_MODE = 0;
    
	/** The ID of the ServerConnection corresponding to the row ID in the local SQLite database. */
	private Long mID;
	/** The time and date of the last time the ServerConnection was connected to. */
	private String mLastUse;
	/** The name of the ServerConnection. */
	private String mName;
	/** The full address to the server this ServerConnection connects to. Used with simple address mode. */
	private String mSimpleAddress;
	/** The IP address to the server this ServerConnection connects to. Used with exploded address mode. */
	private String mIP;
	/** The port number to the server this ServerConnection connects to. Used with exploded address mode. */
	private String mPort;
	/** The path to the geospatial server. Used with exploded address mode. */
	private String mPath;
	/** The workspace to use on the geospatial server. Used with exploded address mode. */
	private String mWorkspace;
	/** The address mode. 1 means simple address mode, where only the full address as a single input String is required. 0 means exploded mode, requiring IP, port, path and workspace name as separate input. */
	private int mMode;
	/** The full server address, formed by combining the address parts according to the address mode. */
	private String mFullAddress;
	
	/**
	 * Constructor for ServerConnection objects, taking IP and port Strings
	 * as inputs and setting those as the instance variables.
	 * 
	 * @param _id The ID of the ServerConnection info from the local SQLite Database.
	 * @param lastuse The last time this ServerConnection was used to access the server. 
	 * @param name The user specified name of the connection.
	 * @param simple The full (simple address mode) address to the geospatial server.
	 * @param ip IP address to assign the ServerConnection object.
	 * @param port Port number to assign the ServerConnection object.
	 * @param path Logical path to the geospatial server to assign the ServerConnection object.
	 * @param workspace Optional workspace on the geospatial server.
	 * @param mode Whether to use simple (1) or exploded (0) address mode.
	 */
	public ServerConnection(Long _id, String lastuse, String name, String simple, String ip, String port, String path, String workspace, int mode) {
		Log.d(TAG, "ServerConnection(" + String.valueOf(_id) + ", " + lastuse + ", " + name + ", " + simple + ", " + ip + ", " + port + ", " + path + ", " + workspace + ", " + String.valueOf(mode) + ") called.");
		
		/* Set the member fields */
		mID = _id;
		mLastUse = lastuse;
		mName = name;
		mSimpleAddress = simple;
		mIP = ip;
		mPort = port;
		mPath = path;
		mWorkspace = workspace;
		mMode = mode;
		
		/* Form the full server address according to the address mode. */
		if(mMode == SIMPLE_ADDRESS_MODE) {
			mFullAddress = mSimpleAddress;
			if(getWorkspace() != null)
				if(!getWorkspace().equalsIgnoreCase(""))
					mFullAddress = mFullAddress + "/" + mWorkspace; // If there is a workspace, append it to the end of the simple address.
		
			/* Insert the port number between the host name and the path. */
			try {
				URI uri = new URI(mFullAddress);
				mFullAddress = uri.getScheme() + "://" + uri.getHost() + ((mPort != null) ? ":" + mPort : "") + uri.getPath();
			} catch (URISyntaxException e) { Log.e(TAG, "Invalid simple address: " + e.toString()); mFullAddress = ""; }
		}
		else {
			mFullAddress = "http://" + getIP() + ":" + getPort() + getPath();
			if(getWorkspace() != null)
				if(!getWorkspace().equalsIgnoreCase(""))
					mFullAddress = mFullAddress + "/" + getWorkspace();
		}		
	}
	
	/**
	 * Get method for the ServerConnection ID.
	 * @return The ID of the ServerConnection in the local SQLite Database.
	 */
	public Long getID() {
		return mID;			
	}
	
	/**
	 * Get method for the field storing the last time the ServerConnection was used to connect to the server.
	 * @return The date and time of the last server.
	 */
	public String getLastUse() {
		return mLastUse;			
	}

	/**
	 * Get method for the user specified ServerConnection name.
	 * @return The ServerConnection name as a String.
	 */
	public String getName() {
		return mName;			
	}
	
	/**
	 * Get method to get the IP address of the ServerConnection object.
	 * @return The IP address as a String.
	 */
	public String getIP() {
		return mIP;			
	}
		
	/**
	 * Get method to get the port number of the ServerConnection object.
	 * @return The port number as a String.
	 */
	public String getPort() {
		return mPort;
	}
	
	/**
	 * Get method to get the geospatial server path of the ServerConnection object.
	 * @return The path as a String.
	 */
	public String getPath() {
		return mPath;
	}
	
	/**
	 * Get method to get the optional workspace on the geospatial server.
	 * @return The workspace as a String.
	 */
	public String getWorkspace() {
		return mWorkspace;
	}

	/**
	 * Get method for the simple server address used in
	 * simple address mode.
	 * @return The full address to the geospatial server.
	 */
	public String getSimpleAddress() {
		return mSimpleAddress;
	}

	/**
	 * Set method for the full server address used in simple address mode.
	 * @param simpleAddress The full address to the geospatial server.
	 * @return Returns true if the set call was successful, else: false.
	 */
	public boolean setSimpleAddress(String simpleAddress) {
		if(Utilities.isValidAddress(simpleAddress)) {
			mSimpleAddress = simpleAddress;
			return true;
		}
		else return false;
	}

	/**
	 * Get method for the address mode of this ServerConnection.
	 * 1 means simple address mode and 0 means exploded mode.
	 * @return The address mode of this ServerConnection.
	 */
	public int getMode() {
		return mMode;
	}
	
	/**
	 * Gets the full server address, a combination of
	 * all the address parts.
	 * @return The full server address, including port number and workspace if applicable.
	 */
	public String getAddress() {
		return mFullAddress;
	}	
}
