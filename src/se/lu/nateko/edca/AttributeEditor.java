package se.lu.nateko.edca;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

import se.lu.nateko.edca.BackboneSvc.SvcAccessor;
import se.lu.nateko.edca.svc.GeographyLayer.LayerField;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

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
 * Activity for displaying a form for entering attribute data and saving it	*
 * to the active layer.														*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.45, 2013-07-24												*
 *																			*
 ****************************************************************************/
public class AttributeEditor extends Activity {
	/** The error tag for this Activity. */
	public static final String TAG = "AttributeEditor";
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

    /** The id of the geometry for which to add attributes. */    
    private long mGeomId;
    /** The number of fields to accomodate for in the attribute editor. */
    private int mNumFields;
    /** Whether or not the user will be able to input coordinates. */
    private boolean mManualCoordinates;
    /** String array of user inputs to preset the EditText boxes to. */
    private String[] mInputPreset;
    /** Flag showing whether or not editing is enabled. */
    private boolean mEnabled;
    
    /** The LinearLayout which contains the containers of each TextView-EditText pair, stored for easier access. */
    private LinearLayout mFormContainer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate(Bundle) called.");
		super.onCreate(savedInstanceState);
		
		/* Set instance state. Use savedInstanceState if possible, otherwise
		 * use Intent extras. If there is neither use defaults. */
		Bundle instanceState = savedInstanceState;
        if(instanceState == null) {
        	instanceState = getIntent().getExtras();
        	if(instanceState == null)
        		fillBundle(instanceState, -1, 0, false, null, false);
        }
        mGeomId = instanceState.getLong(BackboneSvc.PACKAGE_NAME + ".geom");
        mNumFields = instanceState.getInt(BackboneSvc.PACKAGE_NAME + ".numfields");
        mManualCoordinates = instanceState.getBoolean(BackboneSvc.PACKAGE_NAME + ".manualcoords");
        mInputPreset = instanceState.getStringArray(BackboneSvc.PACKAGE_NAME + ".input");
        mEnabled = instanceState.getBoolean(BackboneSvc.PACKAGE_NAME + ".enabled");
        Log.v(TAG, "instanceState Bundle: mGeomId: " + String.valueOf(mGeomId) + ", mNumFields: " + String.valueOf(mNumFields) + ", mManualCoordinates: " + String.valueOf(mManualCoordinates) + ", mEnabled: " + String.valueOf(mEnabled));

    	if(mInputPreset == null)
    		Log.v(TAG, "mInputPreset: null");
    	else {
    		String in = "";
    		for(int i=0; i < mInputPreset.length; i++)
    			in = in + mInputPreset[i] + ", ";
    		Log.v(TAG, "mInputPreset: " + in);
    	}
		
		setContentView(R.layout.attributeeditor);
		
		mFormContainer = (LinearLayout) findViewById(R.id.attributeeditor_formcontainer);
		
		populateAttributeForm();
	}

	@Override
	protected void onResume() {
		/* Bind to the BackboneSvc Service. */
		Intent dbManagerIntent = new Intent(AttributeEditor.this, BackboneSvc.class);		        
	    bindService(dbManagerIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
		
	    super.onResume();
	}

	/**
	 * Continuation of the create/resume process, called from onServiceConnected
	 * after having bound to the Service.
	 */
	protected void onBound() {
		Log.d(TAG, "onBound() called.");
		mService.setActiveActivity(AttributeEditor.this);

		setupAttributeForm(mInputPreset);
		enableAttributeForm(mEnabled);
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause() called.");
		unbindService(mServiceConnection);
//		Log.v(TAG, "unbindService(mDBManagerConnection) finished.");
		super.onPause();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.d(TAG, "Saving InstanceState.");
		
		fillBundle(outState, mGeomId, mNumFields, mManualCoordinates, getText(), mEnabled);
	}
	
	/**
	 * Convenience method for adding extras to a bundle that can be
	 * passed to intents that start this activity.
	 * @param outState The bundle to add extras to. If null, a new Bundle will be created.
	 * @param geom The id of the geometry to add attributes to. Must not be null.
	 * @param numFields The number of non-geometry fields to display.
	 * @param manualCoords Whether or not the coordinates (of a point) should be editable.
	 * @param input A string array with text to pre-enter into the EditText boxes.
	 * @param enabled Boolean deciding whether or not the EditText boxes should be enabled initially.
	 */
	public static Bundle fillBundle(Bundle outState, long geom, int numFields, boolean manualCoords, String[] input, boolean enabled) {
		if(outState == null)
			outState = new Bundle();
		outState.putLong(BackboneSvc.PACKAGE_NAME + ".geom", geom);
		outState.putInt(BackboneSvc.PACKAGE_NAME + ".numfields", numFields);
		outState.putBoolean(BackboneSvc.PACKAGE_NAME + ".manualcoords", manualCoords);
		outState.putStringArray(BackboneSvc.PACKAGE_NAME + ".input", input);
		outState.putBoolean(BackboneSvc.PACKAGE_NAME + ".enabled", enabled);
		
		return outState;
	}
	
	/**
     * onClick callback method called when the Save attributes button is clicked.
     * @param view The view of the Save attributes button.
     */
	public void onClickSave(View view) {
		Log.d(TAG, "onClickSave() called.");
		
		if(mEnabled) { // If the attribute forms are enabled.
			
			/* Fetch and check the coordinate input. */
			String lon = "";
			String lat = "";
			if(mManualCoordinates) {
				LinearLayout fieldForm = (LinearLayout) mFormContainer.getChildAt(0);
				lon = ((EditText) fieldForm.getChildAt(1)).getText().toString();
				fieldForm = (LinearLayout) mFormContainer.getChildAt(1);
				lat = ((EditText) fieldForm.getChildAt(1)).getText().toString();
				
				if(!Utilities.isLongitude(lon)) { // If the longitude is invalid.
					Log.i(TAG, "Invalid longitude. Alert the user and abort.");
					mService.showAlertDialog(getString(R.string.attributeeditor_inputalert_lon), this);
					return;
				}
				else if(!Utilities.isLatitude(lat)){ // If the latitude is invalid.
					Log.i(TAG, "Invalid latitude. Alert the user and abort.");
					mService.showAlertDialog(getString(R.string.attributeeditor_inputalert_lat), this);
					return;
				}
			}
			
			/* Fetch and check the attribute input. */
			ArrayList<String[]> list = new ArrayList<String[]>();
			for(int i=0; i < mNumFields; i++) {
				LinearLayout fieldForm = (LinearLayout) mFormContainer.getChildAt(i + ((mManualCoordinates) ? 2 : 0));
				String fieldName = mService.getActiveLayer().getNonGeomFields().get(i).getName();
				String fieldType = mService.getActiveLayer().getNonGeomFields().get(i).getType();
				String fieldText = ((EditText) fieldForm.getChildAt(1)).getText().toString();
				
				/* Check for forbidden nulls. */
				if(!mService.getActiveLayer().getNonGeomFields().get(i).getNullable() && fieldText.contentEquals("")) {
					Log.i(TAG, "Non-nullable field is null. Alert the user and abort.");
					mService.showAlertDialog(getString(R.string.attributeeditor_inputalert_null) + " " + fieldName, this);
					return;
				}
				/* Check for semicolons.*/
				else if(fieldText.contains(";")) {
					Log.i(TAG, "Text contains invalid input. Alert the user and abort.");
					mService.showAlertDialog(getString(R.string.attributeeditor_inputalert_semicolon), this);
					return;
				}
				/* Check if a date field has an invalid date.*/
				else if(Utilities.dropColons(fieldType, Utilities.RETURN_LAST).equalsIgnoreCase("date")) {
					if(!fieldText.equalsIgnoreCase("")) {
						/* Check if the date can be parsed correctly.*/
						try{
							if(!Utilities.isValidDate(fieldText, Utilities.DATE_SHORT)) {
								Log.v(TAG, "Invalid date. Alert the user and abort.");
								mService.showAlertDialog(getString(R.string.attributeeditor_inputalert_date) + " " + fieldName, this);
								return;
							}
							else {
								fieldText = Utilities.DATE_SHORT.format(Utilities.DATE_SHORT.parse(fieldText));
								((EditText) fieldForm.getChildAt(1)).setText(fieldText);
							}
						} catch(ParseException e) { Log.e(TAG, e.toString()); }
					}
				}
				/* Check if a datetime field has an invalid datetime.*/
				else if(Utilities.dropColons(fieldType, Utilities.RETURN_LAST).equalsIgnoreCase("datetime")) {
					if(!fieldText.equalsIgnoreCase("")) {
						/* Check if the datetime can be parsed correctly. Both with or without "T" as a separator between date and time is acceptable. */
						try{
							if(!Utilities.isValidDate(fieldText, Utilities.DATE_MEDIUM) && !Utilities.isValidDate(fieldText, Utilities.DATE_MEDIUM_T)) {
								Log.i(TAG, "Invalid datetime. Alert the user and abort.");
								mService.showAlertDialog(getString(R.string.attributeeditor_inputalert_datetime) + " " + fieldName, this);
								return;
							}
							else {
								fieldText = Utilities.isValidDate(fieldText, Utilities.DATE_LONG) ? Utilities.DATE_LONG.format(Utilities.DATE_LONG.parse(fieldText))
										: Utilities.isValidDate(fieldText, Utilities.DATE_LONG_T) ? Utilities.DATE_LONG_T.format(Utilities.DATE_LONG_T.parse(fieldText))
												: Utilities.isValidDate(fieldText, Utilities.DATE_MEDIUM) ? Utilities.DATE_MEDIUM.format(Utilities.DATE_MEDIUM.parse(fieldText))+":00"
														: Utilities.DATE_MEDIUM_T.format(Utilities.DATE_MEDIUM_T.parse(fieldText))+":00";
								fieldText = fieldText.replace(" ", "T");
								((EditText) fieldForm.getChildAt(1)).setText(fieldText);
							}
						} catch(ParseException e) { Log.e(TAG, e.toString()); }
					}
				}
				/* Check if an integer field has an invalid input.*/
				else if(Utilities.dropColons(fieldType, Utilities.RETURN_LAST).equalsIgnoreCase("int")) {
					if(!(Utilities.isInteger(fieldText)[0] == 1) && !fieldText.equalsIgnoreCase("")) {
						Log.i(TAG, "Invalid integer. Alert the user and abort.");
						mService.showAlertDialog(getString(R.string.attributeeditor_inputalert_integer) + " " + fieldName, this);
						return;
					}
				}
				/* Check if a double field has an invalid input.*/
				else if(Utilities.dropColons(fieldType, Utilities.RETURN_LAST).equalsIgnoreCase("double") && !fieldText.equalsIgnoreCase("")) {
					try {
						Double.parseDouble(fieldText);
					} catch (NumberFormatException e) { Log.e(TAG, "Invalid double. Alert the user and abort.");
						mService.showAlertDialog(getString(R.string.attributeeditor_inputalert_double) + " " + fieldName, this);
						return;
					}
				}
				/* Check if a boolean field has an invalid input.*/
				else if(Utilities.dropColons(fieldType, Utilities.RETURN_LAST).equalsIgnoreCase("boolean") && !fieldText.equalsIgnoreCase("")) {
					try {
						fieldText = Utilities.fixBoolean(fieldText);
						((EditText) fieldForm.getChildAt(1)).setText(fieldText);
					} catch (IllegalArgumentException e) { Log.e(TAG, "Invalid boolean. Alert the user and abort.");
						mService.showAlertDialog(getString(R.string.attributeeditor_inputalert_boolean) + " " + fieldName, this);
						return;
					}
				}
				list.add(new String[]{fieldName, fieldText});
			}
			
			/* All checks passed; add the geometry and attributes to the active layer. */
			if(mManualCoordinates)
				mService.getActiveLayer().addGeometry(new LatLng(Double.parseDouble(lat), Double.parseDouble(lon)), mGeomId);
			for(int j=0; j < list.size(); j++)
				mService.getActiveLayer().addAttribute(mGeomId, list.get(j)[0], list.get(j)[1]);

			enableAttributeForm(false); // Lock the EditText fields.
		}
		else {
			enableAttributeForm(true); // Unlock the EditText fields.
		}
		
	}
	
	/**
	 * Add an attribute form for each attribute of the active layer,
	 * plus two for the coordinate if applicable.
	 */
    public void populateAttributeForm() {
    	Log.d(TAG, "populateAttributeForm() called.");
    	
    	LinearLayout fieldRow;    	
    	for(int i=0; i < mNumFields + ((mManualCoordinates) ? 2 : 0); i++) {
    		fieldRow = (LinearLayout) View.inflate(this, R.layout.attribute_formrow, null);
    		mFormContainer.addView(fieldRow);
    	}
    }
    
    /**
     * Performs setup of the attribute form, setting the header and EditText contents.
     * Must be called after populateAttributeForm().
     * @param input A string array with any EditText content that should be entered.
     */
    public void setupAttributeForm(String[] input) {
    	Log.d(TAG, "setupAttributeForm() called.");
    	
    	if(mManualCoordinates) { // If manual coordinate input mode is on, setup the two first input rows as the longitude and latitude.
    		LatLng p = mService.getActiveLayer().getGeometry().get(mGeomId);
    		LinearLayout top = (LinearLayout) mFormContainer.getChildAt(0);
    		((TextView) top.getChildAt(0)).setText(getString(R.string.attributeeditor_attform_longitude_title));
    		((EditText) top.getChildAt(1)).setHint(Utilities.dropColons(getString(R.string.attributeeditor_attform_longitude_hint), Utilities.RETURN_LAST));
    		((EditText) top.getChildAt(1)).setText((input == null) ? ((p != null) ? String.valueOf(p.longitude) : "")
    				: (input[0] == null) ? ""
    						: input[0]);
    				
    		top = (LinearLayout) mFormContainer.getChildAt(1);
    		((TextView) top.getChildAt(0)).setText(getString(R.string.attributeeditor_attform_latitude_title));
    		((EditText) top.getChildAt(1)).setHint(Utilities.dropColons(getString(R.string.attributeeditor_attform_latitude_hint), Utilities.RETURN_LAST));
    		((EditText) top.getChildAt(1)).setText((input == null) ? ((p != null) ? String.valueOf(p.latitude) : "")
    		: (input[1] == null) ? ""
    				: input[1]);
    	}
    	
    	// If the coordinate input rows are added, start the automatic setup at index 2.
    	for(int i = (mManualCoordinates) ? 2 : 0; i < ((mNumFields) + ((mManualCoordinates) ? 2 : 0)); i++) {
    		LayerField field = mService.getActiveLayer().getNonGeomFields().get(i-((mManualCoordinates) ? 2 : 0));
    		LinearLayout top = (LinearLayout) mFormContainer.getChildAt(i);
    		((TextView) top.getChildAt(0)).setText(field.getName());
    		((EditText) top.getChildAt(1)).setHint(getString(R.string.attributeeditor_attform_content_input) + Utilities.dropColons(field.getType(), Utilities.RETURN_LAST)); // Add a hint to describe what type of input to enter.
    		/* Try to fill the EditTexts with attributes from the String array parameter,
    		 * or stored attributes for this geometry, in that order. If a dateTime field
    		 * has neither input nor stored attributes; suggest the current time. */
    		try{
    			String att = "";
    			if(input != null) // If there is input, use it.
    				att = (input[i] != null) ? input[i] : "";
    			else if(mService.getActiveLayer().hasAttribute(mGeomId, field.getName())) // Otherwise, if there are attributes, use them.
    				att = mService.getActiveLayer().getAttributes().get(mGeomId).get(field.getName());
    			else if(Utilities.dropColons(field.getType(), Utilities.RETURN_LAST).equalsIgnoreCase("datetime")) // Otherwise, if the field is a dateTime field, suggest the current time.
    				att = Utilities.DATE_LONG.format(new Date());
    			else // Otherwise use the empty String.
    				att = ""; 

    			if(!att.equalsIgnoreCase("")) {
    				((EditText) top.getChildAt(1)).setText(att);
    			}

    		} catch (NullPointerException e) { Log.w(TAG, e.toString()); }
    	}
    }
	
	/**
	 * Enables or disables the attribute form.
	 * @param enable True to enable the form.
	 */
	private void enableAttributeForm(boolean enable) {
		Log.d(TAG, "enableAttForms(" + String.valueOf(enable) + ") called.");
		mEnabled = enable;
		
		((Button) findViewById(R.id.attributeeditor_button_save)).setText(
				(enable) ? R.string.attributeeditor_button_save : R.string.attributeeditor_button_edit);

		for(int i=0; i < mFormContainer.getChildCount(); i++) {
			EditText form = (EditText) ((LinearLayout) mFormContainer.getChildAt(i)).getChildAt(1);
			form.setEnabled(enable);
		}
	}
	
	/**
	 * Gets any text input by the user and returns it in a String array.
	 * @return A string array of all user input.
	 */
	private String[] getText() {
		String[] text = new String[mFormContainer.getChildCount()];
		
		/* Go through all the EditText input boxes and get their contents. */
		for(int i=0; i < text.length; i++) {
			EditText form = (EditText) ((LinearLayout) mFormContainer.getChildAt(i)).getChildAt(1);
			text[i] = form.getText().toString();
			Log.v(TAG, "Fetched input (" + String.valueOf(i) + "): " + text[i]);
		}
		
		return text;
	}
}
