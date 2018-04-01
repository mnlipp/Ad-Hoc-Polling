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
import java.util.HashMap;
import java.util.Map;

import org.jgrapes.core.Channel;
import org.jgrapes.core.CompletionEvent;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.portal.PortalSession;
import org.jgrapes.portal.Portlet;
import org.jgrapes.portal.events.AddPortletRequest;
import org.jgrapes.portal.events.PortalConfigured;
import org.jgrapes.portal.events.PortalPrepared;
import org.jgrapes.portal.events.RenderPortlet;
import org.jgrapes.portal.events.RenderPortletRequest;

/**
 * 
 */
public class NewPortalSessionPolicy extends Component {

    class AddedPreview extends CompletionEvent<AddPortletRequest> {

        public AddedPreview(AddPortletRequest monitoredEvent) {
            super(monitoredEvent);
        }
    }

    /**
     * Creates a new component with its channel set to itself.
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
    public void onPortalPrepared(PortalPrepared event,
            PortalSession portalSession) {
        portalSession.setAssociated(NewPortalSessionPolicy.class,
            new HashMap<String, String>());
    }

    @Handler
    public void onRenderPortlet(RenderPortlet event,
            PortalSession portalSession) {
        if (event.portletClass().getName()
            .equals("de.mnl.ahp.portlets.management.AdminPortlet")) {
            switch (event.renderMode()) {
            case Preview:
            case DeleteablePreview:
                portalSession.associated(NewPortalSessionPolicy.class,
                    () -> new HashMap<String, String>())
                    .put("AdminPreview", event.portletId());
                break;
            case View:
                portalSession.associated(NewPortalSessionPolicy.class,
                    () -> new HashMap<String, String>())
                    .put("AdminView", event.portletId());
                break;
            default:
                break;
            }
        }
    }

    @Handler
    public void onPortalConfigured(PortalConfigured event,
            PortalSession portalSession)
            throws InterruptedException, IOException {
        final Map<String, String> found
            = portalSession.associated(NewPortalSessionPolicy.class,
                () -> new HashMap<String, String>());
        portalSession.setAssociated(NewPortalSessionPolicy.class, null);
        String previewId = found.get("AdminPreview");
        String viewId = found.get("AdminView");
        if (previewId != null && viewId == null) {
            fire(new RenderPortletRequest(event.event().event().renderSupport(),
                previewId, Portlet.RenderMode.View, false),
                portalSession);
            return;
        }
        AddPortletRequest addReq = new AddPortletRequest(
            event.event().event().renderSupport(),
            "de.mnl.ahp.portlets.management.AdminPortlet",
            Portlet.RenderMode.Preview).addProperty("Deletable", false);
        addReq.addCompletionEvent(new AddedPreview(addReq));
        fire(addReq, portalSession);
    }

    @Handler
    public void onAddedPreview(AddedPreview event, PortalSession portalSession)
            throws InterruptedException {
        AddPortletRequest completed = event.event();
        fire(new RenderPortletRequest(completed.renderSupport(),
            completed.get(), Portlet.RenderMode.View, false),
            portalSession);
    }
}
