/*
 * Ad Hoc Polling Application
 * Copyright (C) 2018 Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package de.mnl.ahp.participantui;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import org.jdrupes.httpcodec.util.FormUrlDecoder;
import org.jgrapes.http.events.Request;
import org.jgrapes.io.events.Input;

/**
 *
 */
public class PostedUrlDataDecoder extends FormUrlDecoder {

    private Request.In.Post request;

    public PostedUrlDataDecoder(Request.In.Post request) {
		this.request = request;
	}

	/**
	 * Gets the request.
	 *
	 * @return the request
	 */
    public Request.In.Post request() {
		return request;
	}

	public Optional<Map<String,String>> process(Input<ByteBuffer> event) {
		addData(event.data());
		if (event.isEndOfRecord()) {
			return Optional.of(fields());
		}
		return Optional.empty();		
	}
}
