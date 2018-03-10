/*
 * Ad Hoc Polling Application
 * Copyright (C) 2018  Michael N. Lipp
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

package de.mnl.ahp.application;

import java.io.IOException;

import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.portal.PortalSession;
import org.jgrapes.portal.events.PortalConfigured;
import org.jgrapes.portal.events.PortalPrepared;
import org.jgrapes.portal.events.RenderPortlet;

/**
 * 
 */
public class NewPortalSessionPolicy extends Component {

	/**
	 * Creates a new component with its channel set to
	 * itself.
	 */
	public NewPortalSessionPolicy() {
	}

	/**
	 * Creates a new component with its channel set to the given channel.
	 * 
	 * @param componentChannel
	 */
	public NewPortalSessionPolicy(Channel componentChannel) {
		super(componentChannel);
	}

	@Handler
	public void onPortalPrepared(PortalPrepared event, PortalSession portalSession) {
		portalSession.setAssociated(NewPortalSessionPolicy.class, false);
	}
	
	@Handler
	public void onRenderPortlet(RenderPortlet event, PortalSession portalSession) {
	}
	
	@Handler
	public void onPortalConfigured(PortalConfigured event, PortalSession portalSession) 
			throws InterruptedException, IOException {
	}

}
