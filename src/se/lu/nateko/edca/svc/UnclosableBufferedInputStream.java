package se.lu.nateko.edca.svc;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

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
 * Extended BufferedInputStream made from a regular InputStream, which can	*
 * be reused any number of times.											*
 * 																			*
 * @author Mattias Spångmyr													*
 * @version 0.05, 2012-06-07												*
 * 																			*
 ****************************************************************************/
public class UnclosableBufferedInputStream extends BufferedInputStream {
	/* The error tag for this BufferedInputStream. */
//	private String TAG = "UnclosableBufferedInputStream";

	/** The size of the buffer in bytes. */
	private static final int BUFFERSIZE = 16384;
	
	/**
	 * Constructor taking a regular InputStream to wrap around.
	 * @param in The InputStream to make reusable.
	 */
    public UnclosableBufferedInputStream(InputStream in) {
        super(in, BUFFERSIZE);
        super.mark(Integer.MAX_VALUE);
    }

    @Override
    public void close() throws IOException {
        super.reset();
    }
}
