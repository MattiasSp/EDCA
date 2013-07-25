package se.lu.nateko.edca;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Abstract class holding some static utility methods to avoid cluttering	*
 * other classes.															*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.47, 2013-07-25												*
 * 																			*
 ****************************************************************************/
public abstract class Utilities {
	/** The error tag for this class. */
	public static final String TAG = "Utilities";
	
	/** Constant date format to use for recognizing dates. */
	public static final SimpleDateFormat DATE_SHORT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
	/** Constant datetime format to use for recognizing date and time without seconds. */
	public static final SimpleDateFormat DATE_MEDIUM = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
	/** Constant datetime format to use for recognizing date and time without seconds, with the letter "T" as separator between date and time. */
	public static final SimpleDateFormat DATE_MEDIUM_T = new SimpleDateFormat("yyyy-MM-dd\'T\'HH:mm", Locale.US);
	/** Constant datetime format to use for recognizing date and time including seconds. */
	public static final SimpleDateFormat DATE_LONG = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
	/** Constant datetime format to use for recognizing date and time including seconds, with the letter "T" as separator between date and time. */
	public static final SimpleDateFormat DATE_LONG_T = new SimpleDateFormat("yyyy-MM-dd\'T\'HH:mm:ss", Locale.US);
	
	/** Flag constant for selecting to return the first part of a String that is being split using the dropColons(String) method. */
	public static final int RETURN_FIRST = -2;
	/** Flag constant for selecting to return the last part of a String that is being split using the dropColons(String) method. */
	public static final int RETURN_LAST = -1;
	/** Flag constant for selecting to return all parts of a String that is being split using the dropColons(String) method. */
	public static final int RETURN_ALL = 0;
	
	/**
	 * Method for checking if a given String contains a Double.
	 * @param input String to be examined.
	 * @return Integer: "1" if input contains an integer, else: "0". The second int in the array is the integer found in the input, if applicable.
	 */
	public static int[] isInteger(String input) {
		int[] result = new int[2]; // Initiate return variable.
		try {			
			result[1] = Integer.parseInt(input); // Try to set the second position to the integer value of the input string.
			result[0] = 1; // Set the first position to 1 for true.
			return result;
		}	catch(NumberFormatException e) {
			Log.w(TAG, e.toString());
			result[0] = 0; // Set the first position to 0 for true.
			return result;
		}
	}
	
	/**
	 * Method for conforming an input to a boolean String format. It will return
	 * the last boolean compatible substring in the input as 'true' or 'false'.
	 * @param input The String to transform to a boolean compatible String ('true' or 'false').
	 * @return The transformed boolean compatible String ('true' or 'false').
	 * @throws IllegalArgumentException Thrown if the String input could not be transformed to a boolean compatible String.
	 */
	public static String fixBoolean(String input) throws IllegalArgumentException {
//		Log.v(TAG, "fixBoolean(String) throws IllegalArgumentException called.");
		int truth = (input.indexOf("True") < input.indexOf("true")) ? input.indexOf("true") : input.indexOf("True");
		int fallacy = (input.indexOf("False") < input.indexOf("false")) ? input.indexOf("false") : input.indexOf("False");
		if(truth > fallacy)
			return "true";
		else if(fallacy > truth)
			return "false";
		else
			throw new IllegalArgumentException("Input String does not contain a boolean.");
	}
	
	/**
	 * Method for checking if a given String contains a longitude value.
	 * @param longitude String to be examined.
	 * @return True if the input longitude is valid.
	 */
	public static boolean isLongitude(String longitude) {
		try {			
			double lon = Double.parseDouble(longitude); // Try to parse the input longitude as a double.
			return ((lon <= 180) && (lon >= -180)) ? true : false;
		}	catch(NumberFormatException e) {
			Log.w(TAG, e.toString());
			return false;
		}
	}
	
	/**
	 * Method for checking if a given String contains a latitude value.
	 * @param latitude String to be examined.
	 * @return True if the input latitude is valid.
	 */
	public static boolean isLatitude(String latitude) {
		try {			
			double lat = Double.parseDouble(latitude); // Try to parse the input latitude as a double.
			return ((lat <= 90) && (lat >= -90)) ? true : false;
		}	catch(NumberFormatException e) {
			Log.w(TAG, e.toString());
			return false;
		}
	}
	
	/**
	 * Check that the input String contains four integers 0-255 separated by dots.
	 * @param input String to be checked.
	 * @return Boolean: True if the input contains an IP address.
	 */
	public static boolean isIP(String input) {
		if(input == null || input.equalsIgnoreCase("")) return false; // Null argument is not an IP.
		
		if(input.charAt(0) == '.' || input.charAt(input.length()-1) == '.') return false; // Can not start or end with a dot.

		String[] ipArray = input.split("[.]"); // Splits the input into sections.
		int allTrue = 1; // Initiates the variable to hold the result.
		
		if(ipArray.length != 4) // IPs have four sections.
			return false;
		
		/*
		 * Loop through the four sections of the input string and check that each is an integer between 0-255.
		 * Any invalid section will set the result to 0 and break the loop.
		 */
		for(int i=0; i < 4; i++) {
			int[] isInt = isInteger(ipArray[i]); // Check if section i is an integer.
			if(isInt[0] == 0) { // If section i is not integer, set allTrue to 0 for "not IP" and break loop.
				allTrue = 0;
				break;
			}
			else if(isInt[1] < 0 || isInt[1] > 255) { // Else, if section i is not between 0 & 255, set allTrue to 0 for "not IP" and break loop.
				allTrue = 0;
				break;
			}
		}
		
		if(allTrue == 1)
			return true;
		else return false;
	}
	
	/**
	 * Method that checks that an input path string is either empty "",
	 * or that is starts (but does not end) with a slash "/". Also,
	 * sections divided by slashes should not be empty or contain
	 * blank space.
	 * @param input	Path string to check.
	 * @return True if the path is valid, otherwise false.
	 */
	public static boolean isValidPath(String input) {
		if(input.contentEquals("")) // If the path is empty (valid):
			return true;
		else if(input.charAt(0) != '/') // If the non-empty path does not start with a "/" (invalid):
			return false;
		else if(input.charAt(input.length()-1) == '/') // If the non-empty path ends with a "/" (invalid):
			return false;
		else {
			String[] pathArray = input.split("[/]+", -1); // Splits the input into sections.
			
			for(int i=1; i < pathArray.length-1; i++) { // Check each section of the path.
				if(pathArray[i].contentEquals("")) // Section is empty (implying double slashes) (invalid):
					return false;
				else if(pathArray[i].contains(" ")) // Section contains a blank space (invalid):
					return false;
			}
			
			return true; // All requirements met (valid).
		}		
	}
	
	/**
	 * Method that checks that an input path string is not empty "",
	 * and contains no special characters.
	 * @param input	Path string to check.
	 * @return	True if the path is valid, otherwise false.
	 */
	public static boolean isValidWorkspace(String input) {
//		Log.d(TAG, "isValidWorkspace(String) called.");
		Pattern p = Pattern.compile("[^a-z0-9_-]", Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(input);
		boolean b = m.find();

		if(b) {
			Log.w(TAG, "The workspace string has special characters.");
			return false;
		}
		else return true; // No special characters (valid).		
	}
	
	/**
	 * Checks if the supplied string is a valid date.
	 * @param date The date String to check.
	 * @param dateformat The format of the date to compare to.
	 * @return True if the date fits the format.
	 */
	public static boolean isValidDate(String date, DateFormat dateformat) {
		Log.d(TAG, "isValidDate(date, dateformat) called.");
		if(date != null) {
			try {
				dateformat.parse(date);
			} catch(ParseException e) {
				Log.w(TAG, e.toString());
				return false;
			}
			return true;
		}
		else return false; // Null is not a valid date.
	}
	
	/**
	 * Drops all the colon characters from the input string.
	 * @param colonized String containing colon characters.
	 * @param returnMode Which part of the input String to return.
	 * @return String without colon characters.
	 */
	public static String dropColons(String colonized, int returnMode) {
		String[] stringArray = colonized.split("[:]"); // Splits the input into sections.
		switch(returnMode) {
			case RETURN_FIRST:
				return stringArray[0];
			case RETURN_LAST:
				return stringArray[stringArray.length-1];
			case RETURN_ALL:
				String uncolonized = stringArray[0];
				for(int i=1; i < stringArray.length; i++)
					uncolonized = uncolonized + stringArray[i];
				return uncolonized;
			default: { // Return the section in the position given by the returnMode.
				if(stringArray.length > returnMode)
					return stringArray[returnMode];
				else {
					Log.e(TAG, "No such section to return, uncolonized String has " + stringArray.length + " sections.");
					return null;
				}
			}
		}
	}
	
	/**
	 * Gets a subset of elements from an array contained in the specified range.
	 * @param original Original array from which to get a subset.
	 * @param start Start of the new array, inclusive.
	 * @param length The number of elements to fetch.
	 * @return A new copy of a subset of elements from the original array.
	 */
	public static char[] copyOfRange_char(char[] original, int start, int length) {
		char[] newChar = new char[length];
		try {
			for(int i=0; i<length; i++)
				newChar[i] = original[start+i];
		}
		catch(NullPointerException e) {
			Log.e(TAG, "Start or length parameters invalid.");
			return null;
		}
		return newChar;
	}
	
	/**
	 * Converts coordinates in degrees lat/lon (WGS84) to metres x/y (Spherical Mercator)
	 * used by e.g. GoogleMaps.
	 * @param lat The latitude to project to y values.
	 * @param lon The longitude to project to x values.
	 * @return A (double) array with the projected x/y pair of the GoogleMaps projection.
	 */
	public static double[] WGS84toGoogle(double lat, double lon) {
		  double x = lon * 20037508.34 / 180;
		  double y = ((Math.log(Math.tan((90 + lat) * Math.PI / 360)) / (Math.PI / 180)) * 20037508.34) / 180;
		  return new double[] {x, y};
	}
	
	/**
	 * Converts coordinates in metres x/y (Spherical Mercator)
	 * used by e.g. GoogleMaps to degrees lat/lon (WGS84).
	 * @param x The x coordinate to project to longitude.
	 * @param y The y coordinate to project to latitude.
	 * @return A (double) array with the projected lon/lat pair of the GoogleMaps projection.
	 */
	public static double[] GoogletoWGS84(double x, double y) {
		  double lon = (x / 20037508.34) * 180;
		  double lat = 180/Math.PI * (2 * Math.atan(Math.exp(((y / 20037508.34) * 180) * Math.PI / 180)) - Math.PI / 2);
		  return new double[] {lon, lat};
	}

	/**
	 * Returns the provided LatLngBounds as a String fit to insert
	 * into a GetMap request, but coordinates converted to
	 * GoogleMaps projection (Pseudo Spherical Mercator).
	 */
	public static String latLngBoundsToString(LatLngBounds bounds) {
		/* Convert the coordinates to Google Maps Projection (Pseudo Spherical Mercator). */
		double[] minPoint = Utilities.WGS84toGoogle(bounds.southwest.latitude, bounds.southwest.longitude);
		double[] maxPoint = Utilities.WGS84toGoogle(bounds.northeast.latitude, bounds.northeast.longitude);
		
		/* Form the WMS Bounds element string. */
		String wmsbounds = String.valueOf(minPoint[0]) + "," + String.valueOf(minPoint[1]) + ","	+ String.valueOf(maxPoint[0]) + "," + String.valueOf(maxPoint[1]);
//		Log.i(TAG, "Bounds given as string (Google Maps projection, SRS 3857): " + wmsbounds);
		return wmsbounds;
	}
	
	/**
	 * Returns the provided coordinate as a human-readable
	 * String with the format "[Y] N, [X] E".
	 * @param lat The coordinate's latitude.
	 * @param lon The coordinate's longitude.
	 * @return A human-readable String representation of the coordinate.
	 */
	public static String coordinateToString(double lat, double lon) {
		return new String(String.valueOf(lat) + " N, " + String.valueOf(lon) + " E");
	}
	
	/**
	 * Deletes the given file or folder with any sub folders and their files to any depth.
	 * @param fileOrDirectory The file or folder to delete.
	 */
	public static void deleteRecursive(File fileOrDirectory) {
		Log.v(TAG, "deleteRecursive(" + fileOrDirectory.toString() + ") called.");
	    if (fileOrDirectory.isDirectory())
	        for (File child : fileOrDirectory.listFiles())
	            deleteRecursive(child);

	    fileOrDirectory.delete();
	}
	
	/**
	 * Converts a Long (object) array into a long[] (primitive)
	 * array.
	 * @param array The Long object array to convert.
	 * @return A long[] primitive array.
	 */
	public static long[] longToPrimitiveArray(Long[] array) {
		long[] result = new long[array.length];
		for(int i=0; i < result.length; i++)
			result[i] = array[i];
		return result;
	}
	
	/**
	 * Converts a long[] (primitive) array into a Long (object) array.
	 * @param array The long[] primitive array to convert.
	 * @return A Long object array.
	 */
	public static Long[] longToObjectArray(long[] array) {
		Long[] result = new Long[array.length];
		for(int i=0; i < result.length; i++)
			result[i] = array[i];
		return result;
	}
}
