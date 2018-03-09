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

import java.io.Serializable;

/**
 * 
 */
public class VotingController implements Serializable {
	private static final long serialVersionUID = -4844487793046604718L;

	private enum State { Start, Authorized, Voted }
	
	private State state = State.Start;
	
	public String pageToShow() {
		switch(state) {
		case Authorized:
			return "authPage.ftl.html";
		case Voted:
			return "authPage.ftl.html";
		default:
			return "authPage.ftl.html";
		}
	}
}
