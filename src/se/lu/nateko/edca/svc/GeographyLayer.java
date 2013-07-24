package se.lu.nateko.edca.svc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import se.lu.nateko.edca.BackboneSvc;
import se.lu.nateko.edca.Utilities;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Polygon;

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
 * GeographyLayer object class, managed by a BackboneSvc object. It holds	*
 * the information about a layer such as layer geometry type				*
 * (point/line/polygon) and what attribute fields are available as well as	*
 * the actual data, both geometry and attributes.							*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.51, 2013-07-24												*
 * 																			*
 ****************************************************************************/
public class GeographyLayer {
	/** The error tag for this class. */
	public static final String TAG = "GeographyLayer";

	/** Constant identifying the "invalid" geometry type. */
	public static final int TYPE_INVALID = 0;
	/** Constant identifying the "point" geometry type. */
	public static final int TYPE_POINT = 1;
	/** Constant identifying the "line" geometry type. */
	public static final int TYPE_LINE = 2;
	/** Constant identifying the "polygon" geometry type. */
	public static final int TYPE_POLYGON = 3;
	
	/** A reference to the application's background Service, received in the default constructor. */
	private BackboneSvc mService;
	
	/** The name of the layer, as it is specified on the geospatial server. */
	private String mLayerName;
	/** The geometry type of the layer, as it is specified on the geospatial server. */
	private String mLayerType;
	/** The geometry type of the layer, simplified to be either of the constant integer identifiers TYPE_INVALID, TYPE_POINT, TYPE_LINE or TYPE_POLYGON. */
	private int mTypeMode;
	
	/** The point geometry stored by this GeographyLayer. */
	private TreeMap<Long, LatLng> mGeometry = new TreeMap<Long, LatLng>();
	/** The list of point sequences (lines/polygons) stored, which in turn are lists of which points they are made of. */
	private TreeMap<Long, ArrayList<Long>> mPointSequence = new TreeMap<Long, ArrayList<Long>>();
	/** The list of attributes stored by this GeographyLayer. */
	private TreeMap<Long, HashMap<String, String>> mAttributes = new TreeMap<Long, HashMap<String, String>>();
	/** The list of the available fields for the layer this GeographyLayer is based on. */
	private ArrayList<LayerField> mFields = new ArrayList<LayerField>();
	
	/** Whether or not any geometry data is stored in this GeographyLayer. */
	private boolean mHasGeometry = false;
	
	/**
	 * Default constructor leaving the attribute info uninitialized.
	 * @param service The BackboneSvc service reference, required to access the application-wide JTS 1.8 GeometryFactory.
	 * @param layerName The name of the layer as it is specified on the geospatial server.
	 * @param layerType The type of Geometry this layer will contain, as it is specified on the geospatial server.
	 */
	public GeographyLayer(BackboneSvc service, String layerName, String layerType) {
		mService = service;
		setName(layerName);
		mLayerType = layerType;
		
		mTypeMode = getGeometryType(layerType); // Set the type mode.
		
		mHasGeometry = false;
	}
	
	/**
	 * Checks the input type name and returns the corresponding type code.
	 * @param typeString The type name to translate into type.
	 * @return The geometry type.
	 */
	public static int getGeometryType(String typeString) {		
		return	(typeString.equalsIgnoreCase("gml:PointPropertyType") ||
				typeString.equalsIgnoreCase("gml:MultiPointPropertyType"))		?	TYPE_POINT		:
					
				(typeString.equalsIgnoreCase("gml:LineStringPropertyType") ||
				typeString.equalsIgnoreCase("gml:MultiLineStringPropertyType"))	?	TYPE_LINE		:

				(typeString.equalsIgnoreCase("gml:PolygonPropertyType") ||
				typeString.equalsIgnoreCase("gml:MultiPolygonPropertyType") ||
				typeString.equalsIgnoreCase("gml:SurfacePropertyType") ||
				typeString.equalsIgnoreCase("gml:MultiSurfacePropertyType")) ? TYPE_POLYGON	: TYPE_INVALID;
	}
	
	/**
	 * Set method for the layer name.
	 * @param name The layer name.
	 */
	public void setName(String name) {
		mLayerName = name;
	}
	
	/**
	 * Get method for the layer name.
	 * @return The layer name.
	 */
	public String getName() {
		return mLayerName;
	}
	
	/**
	 * Get method for the layer type.
	 * @return The layer type.
	 */
	public String getType() {
		return mLayerType;
	}
	
	/**
	 * Get method for the layer Type Mode.
	 * @return The layer Type Mode.
	 */
	public int getTypeMode() {
		return mTypeMode;
	}
	
	/**
	 * Gets the column name of the geometry column,
	 * or the empty string if there is no matching field.
	 * @return The column name.
	 */
	public String getGeomColumnKey() {
		for(int i=0; i < getFields().size(); i++) {
			if(getFields().get(i).getType().equalsIgnoreCase(mLayerType))
				return getFields().get(i).getName();
		}
		return "";
	}
	
	/**
	 * Gets this layer's geometry map.
	 * @return The geometry.
	 */
	public TreeMap<Long, LatLng> getGeometry() {
		return mGeometry;
	}
	
	/**
	 * Gets any point sequences (lines/polygons) of the layer.
	 * @return This layer's point sequences.
	 */
	public TreeMap<Long, ArrayList<Long>> getPointSequence() {
		if(mPointSequence != null)
				return mPointSequence;
		else {
			mPointSequence = new TreeMap<Long, ArrayList<Long>>();
			return mPointSequence;
		}
	}
	
	/**
	 * Checks whether the given point id is included in any point sequence.
	 * @param id The id to look for.
	 * @return The ID of the sequence where the point is included, or -1 if it isn't.
	 */
	public long pointInSequence(long id) {
		for(Long sequence : getPointSequence().keySet())
				if(getPointSequence().get(sequence).contains(id))
					return sequence;
		return -1;
	}
	
	/**
	 * Clears all geometry and attributes from the GeographyLayer.
	 */
	public void clearGeometry() {
		getGeometry().clear();
		getPointSequence().clear();
		getAttributes().clear();
		mHasGeometry = false;
	}
	
	/**
	 * Sets the layer's geometry to the collection specified.
	 * @param geom The map of geometry objects to set for this layer.
	 */
	public void setGeometry(TreeMap<Long, LatLng> geom) {
		mGeometry = geom;
		mHasGeometry = (geom != null) ? true : false;
	}
	
	/**
	 * Adds a geometry object to this layer with an ID like the last geometry plus one.
	 * @param geom The geometry to add.
	 * @return The ID of the geometry object just added.
	 */
	public long addGeometry(LatLng geom) {
		if(geom != null) {
			long newId = getNewId();
			getGeometry().put(newId, geom);
			if(getTypeMode() == GeographyLayer.TYPE_POINT) // Only add attributes for points if the layer type is point.
				getAttributes().put(newId, new HashMap<String, String>());
			Log.i(TAG, "Added geometry (ID: " + String.valueOf(newId) + "): " + Utilities.coordinateToString(geom.latitude, geom.longitude));
			mHasGeometry = true;
			return mGeometry.lastKey();
		}
		else {
			Log.i(TAG, "No geometry to add.");
			return -1;
		}
	}
	
	/**
	 * Adds a geometry object to this layer with the ID specified.
	 * @param geom The geometry to add.
	 * @param id The ID to set for the geometry object.
	 * @return True if the geometry was added successfully.
	 */
	public boolean addGeometry(LatLng geom, long id) {
		if(geom != null) {
			mGeometry.put(id, geom);
			if(getTypeMode() == GeographyLayer.TYPE_POINT) // Only add attributes for points if the layer type is point.
				getAttributes().put(id, new HashMap<String, String>());
			Log.i(TAG, "Added geometry (ID: " + String.valueOf(id) + "): " + Utilities.coordinateToString(geom.latitude, geom.longitude));
			mHasGeometry = true;
			return true;
		}
		else {
			Log.i(TAG, "No geometry to add.");
			return false;
		}
	}
	
	/**
	 * Adds a line object to this layer with the ID specified.
	 * @param line The line geometry to add.
	 * @param id The ID to set for the geometry object.
	 * @param addGeom True to add new point geometries from this line, false to find and use existing geometry.
	 * @return True if the geometry was added successfully.
	 */
	public boolean addLine(LineString line, long id, boolean addGeom) {
		if(line != null) {
			ArrayList<Long> sequence = new ArrayList<Long>();

			for(int i=0; i < line.getCoordinates().length; i++) { // Go through and add the line's points to the layer geometry.
				if(addGeom) {
					Long newId = getNewId();
					mGeometry.put(newId, new LatLng(line.getCoordinateN(i).y, line.getCoordinateN(i).x));
					sequence.add(newId);
				}
				else {
					for(Long key : mGeometry.keySet()) {
						if(mGeometry.get(key).equals(mService.getGeometryFactory().createPoint(line.getCoordinateN(i))))
							sequence.add(key);
					}
				}
			}
			getPointSequence().put(id, sequence); // Add the line as a point sequence with id from the server and the sequence id:s relating to the newly added points.
			
			getAttributes().put(id, new HashMap<String, String>()); // Add a new attribute HashMap.
			Log.i(TAG, "Added line (ID: " + String.valueOf(id) + "): " + line.toText());
			mHasGeometry = true;
			return true;
		}
		else {
			Log.i(TAG, "No geometry to add.");
			return false;
		}
	}
	
	/**
	 * Adds a polygon object to this layer with the ID specified.
	 * @param polygon The polygon geometry to add.
	 * @param id The ID to set for the geometry object.
	 * @param addGeom True to add new point geometries from this polygon, false to find and use existing geometry. 
	 * @return True if the geometry was added successfully.
	 */
	public boolean addPolygon(Polygon polygon, long id, boolean addGeom) {
		if(polygon != null) {
			ArrayList<Long> sequence = new ArrayList<Long>();
			
			for(int i=0; i < polygon.getCoordinates().length; i++) { // Go through and add the polygon's points to the layer geometry.
				if(addGeom) {
					Long newId = getNewId();
					mGeometry.put(newId, new LatLng(polygon.getExteriorRing().getCoordinateN(i).y, polygon.getExteriorRing().getCoordinateN(i).x));
					sequence.add(newId);
				}
				else {
					for(Long key : mGeometry.keySet()) {
						if(mGeometry.get(key).equals(mService.getGeometryFactory().createPoint(polygon.getExteriorRing().getCoordinateN(i))))
							sequence.add(key);
					}
				}
			}			
			getPointSequence().put(id, sequence); // Add the polygon as a point sequence with id from the server and the sequence id:s relating to the newly added points.
			
			getAttributes().put(id, new HashMap<String, String>()); // Add a new attribute HashMap.
			Log.i(TAG, "Added polygon (ID: " + String.valueOf(id) + "): " + polygon.toText());
			mHasGeometry = true;
			return true;
		}
		else {
			Log.i(TAG, "No geometry to add.");
			return false;
		}
	}
	
	/**
	 * Returns the flag indicating whether or not this GeographyLayer
	 * has Geometry added to it.
	 * @return True if this GeographyLayer contains Geometry data.
	 */
	public boolean hasGeometry() {
		return mHasGeometry;
	}
	
	/**
	 * Checks whether there is an attribute stored in the field
	 * for a given geometry.
	 * @param geomId The geometry to check for an attribute.
	 * @param fieldName The name of the field to check for an attribute in.
	 * @return True if there is an attribute stored in the specified field for a given geometry, otherwise false.
	 */
	public boolean hasAttribute(long geomId, String fieldName) {
		if(getAttributes() == null)
			return false;
		else if(getAttributes().get(geomId) == null)
			return false;
		else if(getAttributes().get(geomId).get(fieldName) == null)
			return false;
		else
			return true;
	}
	
	/**
	 * Method for adding info on a field to the GeographyLayer.
	 * @param name The name of the field.
	 * @param nullable Specifies whether or not this field can be null.
	 * @param dataType The data type of the field, such as Date (xsd:date) or Point (gml:PointPropertyType).
	 */
	public void addField(String name, boolean nullable, String dataType) {
		Log.i(TAG, "Adding field to the layer: " + name + "," + String.valueOf(nullable) + "," + dataType);
		mFields.add(new LayerField(name, nullable, dataType));
	}
	
	/**
	 * Gets the info on this Layer's fields as an ArrayList.
	 * @return The field info of this GeographyLayer.
	 */
	public ArrayList<LayerField> getFields() {
		return mFields;
	}
	
	/**
	 * Gets the field array without the geometry field.
	 * @return The non-geometry fields.
	 */
	public ArrayList<LayerField> getNonGeomFields() {
		@SuppressWarnings("unchecked")
		ArrayList<LayerField> nonGeomFields = (ArrayList<LayerField>) getFields().clone();
		for(int i=0; i < getFields().size(); i++) {
			if(nonGeomFields.get(i).getName().equalsIgnoreCase(getGeomColumnKey())) {
				nonGeomFields.remove(i);
				//Log.i(TAG, "getNonGeomFields() returning list of " + nonGeomFields.size() + " fields.");
				return nonGeomFields;
			}
		}		
		return null; // Returns null if there is no geometry column.
	}
	
	/**
	 * Gets the next ID to use for new geometry, or 1 if it's
	 * the first geometry.
	 * @return The next ID.
	 */
	public long getNewId() {
		return (getGeometry().size() > 0) ? getGeometry().lastKey()+1 : 1;
	}
	
	/**
	 * Gets the attribute HashMap.
	 * @return The attributes.
	 */
	public TreeMap<Long, HashMap<String, String>> getAttributes() {
		return mAttributes;
	}
	
	/**
	 * Adds an attribute to a given geometry.
	 * @param geomId The id of the geometry for which to add attributes. Must match an id in the geometry list.
	 * @param fieldName The name of the attribute field.
	 * @param att The attribute.
	 */
	public void addAttribute(Long geomId, String fieldName, String att) {
		Log.i(TAG, "addAttribute called. ID: " + String.valueOf(geomId) + ". " + fieldName + ": \"" + att + "\"");

		if(getAttributes().get(geomId) != null)
			getAttributes().get(geomId).put(fieldName, att);
		else
			Log.e(TAG, att + " - No attribute HashMap found for these attributes.");
	}
	
	/**
	 * toString() method returning an easily read string representation of the attributes
	 * of this GeographyLayer.
	 */
	@Override
	public String toString() {
		if(getFields().size() < 1)
			return new String("Layer has no attribute fields.");
		else {
			String resultString = "";
			for(int i=0; i < getFields().size(); i++) {
				if(i > 0)
					resultString = resultString + ";";
				resultString = resultString + getFields().get(i).getName() + "," + String.valueOf(getFields().get(i).getNullable()) + "," + getFields().get(i).getType();				
			}
			return resultString;
		}
	}
	
	/**
	 * Simple field class for keeping the name, nullable and type data of a field.
	 * @author Mattias Spångmyr
	 * @version 0.01, 2012-09-17
	 */
	public class LayerField {
		/** The error tag for this class. */
		public static final String TAG = "LayerField";

		/** The name of the field. */
		private final String mName;
		/** Whether or not the field's values can be null. */
		private final boolean mNullable;
		/** The field type, as it is specified on the geospatial server. */
		private final String mType;
		
		/**
		 * Default constructor setting the field data.
		 * @param name The field name.
		 * @param nullable Whether or not null values are allowed.
		 * @param type The data type of the field on the geospatial server.
		 */
		public LayerField(String name, boolean nullable, String type) {
			mName = name;
			mNullable = nullable;
			mType = type;
		}
		
		/**
		 * Gets the field name.
		 * @return The field name.
		 */
		public String getName() {
			return mName;
		}
		
		/**
		 * Gets the nullable value.
		 * @return The nullable value.
		 */
		public boolean getNullable() {
			return mNullable;
		}
		
		/**
		 * Gets the field type.
		 * @return The data type of the field on the geospatial server.
		 */
		public String getType() {
			return mType;
		}
	}

}
