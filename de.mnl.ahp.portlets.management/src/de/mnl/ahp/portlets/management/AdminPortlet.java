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

package de.mnl.ahp.portlets.management;

import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateNotFoundException;

import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

import org.jgrapes.core.Channel;
import org.jgrapes.core.Event;
import org.jgrapes.core.Manager;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.http.Session;
import org.jgrapes.portal.PortalSession;
import org.jgrapes.portal.PortalWeblet;
import org.jgrapes.portal.Portlet.RenderMode;

import static org.jgrapes.portal.Portlet.RenderMode.DeleteablePreview;
import static org.jgrapes.portal.Portlet.RenderMode.View;

import org.jgrapes.portal.events.AddPageResources.ScriptResource;
import org.jgrapes.portal.events.AddPortletRequest;
import org.jgrapes.portal.events.AddPortletType;
import org.jgrapes.portal.events.DeletePortlet;
import org.jgrapes.portal.events.DeletePortletRequest;
import org.jgrapes.portal.events.PortalReady;
import org.jgrapes.portal.events.RenderPortletRequest;
import org.jgrapes.portal.freemarker.FreeMarkerPortlet;

/**
 * A portlet for inspecting the services in an OSGi runtime.
 */
public class AdminPortlet extends FreeMarkerPortlet {

	private static final Set<RenderMode> MODES = RenderMode.asSet(
			DeleteablePreview, View);
	
	/**
	 * Creates a new component with its channel set to the given 
	 * channel.
	 * 
	 * @param componentChannel the channel that the component's 
	 * handlers listen on by default and that 
	 * {@link Manager#fire(Event, Channel...)} sends the event to 
	 */
	public AdminPortlet(Channel componentChannel) {
		super(componentChannel, true);
	}
	
	@Handler
	public void onPortalReady(PortalReady event, PortalSession channel) 
			throws TemplateNotFoundException, MalformedTemplateNameException, 
			ParseException, IOException {
		ResourceBundle resourceBundle = resourceBundle(channel.locale());
		// Add portlet resources to page
		channel.respond(new AddPortletType(type())
				.setDisplayName(resourceBundle.getString("portletName"))
				.addScript(new ScriptResource()
						.setRequires(new String[] {"datatables.net"})
						.setScriptUri(event.renderSupport().portletResource(
								type(), "Admin-functions.ftl.js")))
				.addCss(event.renderSupport(), PortalWeblet.uriFromPath("Admin-style.css"))
				.setInstantiable());
	}
	
	/* (non-Javadoc)
	 * @see org.jgrapes.portal.AbstractPortlet#generatePortletId()
	 */
	@Override
	protected String generatePortletId() {
		return type() + "-" + super.generatePortletId();
	}

	/* (non-Javadoc)
	 * @see org.jgrapes.portal.AbstractPortlet#modelFromSession
	 */
	@SuppressWarnings("unchecked")
	@Override
	protected <T extends Serializable> Optional<T> stateFromSession(
			Session session, String portletId, Class<T> type) {
		if (portletId.startsWith(type() + "-")) {
			return Optional.of((T)new AdminModel(portletId));
		}
		return Optional.empty();
	}

	/* (non-Javadoc)
	 * @see org.jgrapes.portal.AbstractPortlet#doAddPortlet
	 */
	protected String doAddPortlet(AddPortletRequest event, PortalSession channel)
			throws Exception {
		AdminModel portletModel = new AdminModel(generatePortletId());
		Template tpl = freemarkerConfig().getTemplate("Admin-preview.ftl.html");
		channel.respond(new RenderPortletFromTemplate(event,
				AdminPortlet.class, portletModel.getPortletId(),
				tpl, fmModel(event, channel, portletModel))
				.setRenderMode(DeleteablePreview).setSupportedModes(MODES)
				.setForeground(true));
//		channel.respond(new NotifyPortletView(type(),
//				portletModel.getPortletId(), "serviceUpdates", serviceInfos, "preview", true));
		return portletModel.getPortletId();
	}

	/* (non-Javadoc)
	 * @see org.jgrapes.portal.AbstractPortlet#doRenderPortlet
	 */
	@Override
	protected void doRenderPortlet(RenderPortletRequest event,
	        PortalSession channel, String portletId, Serializable retrievedState)
	        throws Exception {
		AdminModel portletModel = (AdminModel)retrievedState;
		switch (event.renderMode()) {
		case Preview:
		case DeleteablePreview: {
			Template tpl = freemarkerConfig().getTemplate("Admin-preview.ftl.html");
			channel.respond(new RenderPortletFromTemplate(event,
					AdminPortlet.class, portletId, 
					tpl, fmModel(event, channel, portletModel))
					.setRenderMode(DeleteablePreview).setSupportedModes(MODES)
					.setForeground(event.isForeground()));
//			channel.respond(new NotifyPortletView(type(),
//					portletModel.getPortletId(), "serviceUpdates", serviceInfos, "preview", true));
			break;
		}
		case View: {
			Template tpl = freemarkerConfig().getTemplate("Admin-view.ftl.html");
			channel.respond(new RenderPortletFromTemplate(event,
					AdminPortlet.class, portletModel.getPortletId(), 
					tpl, fmModel(event, channel, portletModel))
					.setSupportedModes(MODES).setForeground(event.isForeground()));
//			channel.respond(new NotifyPortletView(type(),
//					portletModel.getPortletId(), "serviceUpdates", serviceInfos, "view", true));
			break;
		}
		default:
			break;
		}	
	}

	/* (non-Javadoc)
	 * @see org.jgrapes.portal.AbstractPortlet#doDeletePortlet
	 */
	@Override
	protected void doDeletePortlet(DeletePortletRequest event, PortalSession channel, 
			String portletId, Serializable portletState) throws Exception {
		channel.respond(new DeletePortlet(portletId));
	}

	@SuppressWarnings("serial")
	public class AdminModel extends PortletBaseModel {

		public AdminModel(String portletId) {
			super(portletId);
		}

	}
}
