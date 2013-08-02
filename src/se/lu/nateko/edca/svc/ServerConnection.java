package se.lu.nateko.edca.svc;

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
 * the IP, port, path and workspace name for the server.					*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.51, 2013-08-01												*
 * 																			*
 ****************************************************************************/
public class ServerConnection {
	/** The error tag for this class. */
	public static final String TAG = "ServerConnection";
	/** The ID of the ServerConnection corresponding to the row ID in the local SQLite database. */
	private Long mID;
	/** The time and date of the last time the ServerConnection was connected to. */
	private String mLastUse;
	/** The name of the ServerConnection. */
	private String mName;
	/** The full address to the server this ServerConnection connects to. Used with simple address mode. */
	private String mSimpleAddress;
	/** The IP address to the server this ServerConnection connects to. Used with exploded address mode. */
	private String mIPaddress;
	/** The port number to the server this ServerConnection connects to. Used with exploded address mode. */
	private String mPort;
	/** The path to the geospatial server. Used with exploded address mode. */
	private String mPath;
	/** The workspace to use on the geospatial server. Used with exploded address mode. */
	private String mWorkspace;
	/** The address mode. 1 means simple address mode, where only the full address as a single input String is required. 0 means exploded mode, requiring IP, port, path and workspace name as separate input. */
	private int mMode;
	
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
		setID(_id);
		setLastUse(lastuse);
		setName(name);
		mSimpleAddress = simple;
		setIP(ip);
		setPort(port);
		setPath(path);
		setWorkspace(workspace);
		mMode = mode;
	}
	
	/**
	 * Set method for the ServerConnection ID.
	 * @param id The ID of the ServerConnection in the local SQLite Database.
	 */
	public void setID(Long id) {
		mID = id;
	}
	
	/**
	 * Set method for the field storing the last time the ServerConnection was used to connect to the server.
	 * @param lastuse The date and time of the last connection.
	 */
	public void setLastUse(String lastuse) {
		mLastUse = lastuse;
	}

	/**
	 * Set method for the ServerConnection name.
	 * @param name The user specified name of the ServerConnection.
	 */
	public void setName(String name) {
		mName = name;
	}
	
	/**
	 * Set method to assign an IP address to the ServerConnection object.
	 * @param input IP address String in the format "#.#.#.#" where # is an integer between 0 & 255.
	 * @return Returns true if the set call was successful, else: false.
	 */
	public boolean setIP(String input) {
//		Log.d(TAG, "setIP(String) called.");
		/*
		 * Check that the input String contains four integers 0-255 separated by dots.
		 */
		if(Utilities.isIP(input)) {
			mIPaddress = input;
			return true;
		}	else { return false; }			
	}
	
	/**
	 * Set method to assign a port number to the ServerConnection object.
	 * @param input Port number string where the string contains an integer.
	 * @return Returns true if the set call was successful, else: false.
	 */
	public boolean setPort(String input) {
//		Log.d(TAG, "setPort(String) called.");
		if(Utilities.isInteger(input)[0] == 1) {
			mPort = input;
			return true;
		} else { return false; }
	}
	
	/**
	 * Set method to assign a path to the ServerConnection object.
	 * @param input Path to the geospatial server, a string starting and ending with a slash "/", or empty "".
	 * @return Returns true if the set call was successful, else: false.
	 */
	public boolean setPath(String input) {
//		Log.d(TAG, "setPath(String) called.");
		if(Utilities.isValidPath(input)) {
			mPath = input;
			return true;
		} else { return false; }
	}
	
	/**
	 * Set method to assign a workspace to the ServerConnection object.
	 * @param input Name of the workspace on the geospatial server, a string without special characters such as "/".
	 * @return Returns true if the set call was successful, else: false.
	 */
	public boolean setWorkspace(String input) {
//		Log.d(TAG, "setWorkspace(String) called.");
		if(Utilities.isValidWorkspace(input)) {
			mWorkspace = input;
			return true;
		} else { return false; }
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
		return mIPaddress;			
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
	 * Method for getting the full IP address as a String,
	 * in the format #.#.#.#:?/A/B where "#" should be a positive
	 * integer 0-255, the ? should be a positive integer, the
	 * A should be the logical path to the geospatial server
	 * or an empty string (in which case no slashes are used) and
	 * B should be the workspace name on the server.
	 * 
	 *  @return The full IP address of the server object.
	 */
	public String toString() {
		String fullAddress = new String(getIP() + ":" + getPort() + getPath());
		if(getWorkspace() != null)
			if(!getWorkspace().equalsIgnoreCase(""))
				fullAddress = fullAddress + "/" + getWorkspace();
		return fullAddress;
	}

	/**
	 * Get method for the full server address used in simple address mode.
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
	 * Set method for the address mode of this ServerConnection.
	 * Pass 1 for simple mode, requiring the full server address
	 * as a single string, or pass 0 for exploded mode where the
	 * address need to be entered as separate parts.
	 * @param mode The address mode to set for this ServerConnection.
	 */
	public void setMode(int mode) {
		mMode = mode;
	}
	
/*	/**
	 * Forms the path to the WFS service, which is on the same folder level as
	 * the layer workspaces for e.g. Geoserver. Truncates the path string by
	 * removing the last level and replacing it with "/wfs".
	 * @return The WFS service path as a String.
	 *//*
	public String toString_WFS() {
		String[] wfsPathSections = getPath().split("[/]+", -1); // Splits the input into sections.
		String wfsPath = "";
		for(int i=1; i < (wfsPathSections.length-1); i++) {
			wfsPath = wfsPath + "/" + wfsPathSections[i];
		}
		String wfsAddress = new String(getIP() + ":" + getPort() + wfsPath + "/wfs");
		return wfsAddress;
	}
*/	
	
}
