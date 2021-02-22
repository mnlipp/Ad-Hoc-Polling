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
import org.jgrapes.webconsole.base.Conlet;
import org.jgrapes.webconsole.base.Conlet.RenderMode;
import org.jgrapes.webconsole.base.ConsoleSession;
import org.jgrapes.webconsole.base.events.AddConletRequest;
import org.jgrapes.webconsole.base.events.ConsoleConfigured;
import org.jgrapes.webconsole.base.events.ConsolePrepared;
import org.jgrapes.webconsole.base.events.RenderConlet;
import org.jgrapes.webconsole.base.events.RenderConletRequest;

/**
 * 
 */
public class NewConsoleSessionPolicy extends Component {

    class AddedPreview extends CompletionEvent<AddConletRequest> {

        public AddedPreview(AddConletRequest monitoredEvent) {
            super(monitoredEvent);
        }
    }

    /**
     * Creates a new component with its channel set to itself.
     */
    public NewConsoleSessionPolicy() {
    }

    /**
     * Creates a new component with its channel set to the given channel.
     * 
     * @param componentChannel
     */
    public NewConsoleSessionPolicy(Channel componentChannel) {
        super(componentChannel);
    }

    @Handler
    public void onPortalPrepared(ConsolePrepared event,
            ConsoleSession portalSession) {
        portalSession.setAssociated(NewConsoleSessionPolicy.class,
            new HashMap<String, String>());
    }

    @Handler
    public void onRenderConlet(RenderConlet event,
            ConsoleSession portalSession) {
        if (event.conletType()
            .equals("de.mnl.ahp.conlets.management.AdminConlet")) {
            if (event.renderAs().contains(RenderMode.Preview)) {
                portalSession.associated(NewConsoleSessionPolicy.class,
                    () -> new HashMap<String, String>())
                    .put("AdminPreview", event.conletId());
            }
            if (event.renderAs().contains(RenderMode.View)) {
                portalSession.associated(NewConsoleSessionPolicy.class,
                    () -> new HashMap<String, String>())
                    .put("AdminView", event.conletId());
            }
        }
    }

    @Handler
    public void onConsoleConfigured(ConsoleConfigured event,
            ConsoleSession portalSession)
            throws InterruptedException, IOException {
        final Map<String, String> found
            = portalSession.associated(NewConsoleSessionPolicy.class,
                () -> new HashMap<String, String>());
        portalSession.setAssociated(NewConsoleSessionPolicy.class, null);
        String previewId = found.get("AdminPreview");
        String viewId = found.get("AdminView");
        if (previewId != null && viewId != null) {
            return;
        }
        if (previewId != null && viewId == null) {
            fire(new RenderConletRequest(event.event().event().renderSupport(),
                previewId, RenderMode.asSet(Conlet.RenderMode.View)),
                portalSession);
            return;
        }
        AddConletRequest addReq = new AddConletRequest(
            event.event().event().renderSupport(),
            "de.mnl.ahp.conlets.management.AdminConlet",
            RenderMode.asSet(RenderMode.Preview, RenderMode.StickyPreview));
        addReq.addCompletionEvent(new AddedPreview(addReq));
        fire(addReq, portalSession);
    }

    @Handler
    public void onAddedPreview(AddedPreview event, ConsoleSession portalSession)
            throws InterruptedException {
        AddConletRequest completed = event.event();
        fire(new RenderConletRequest(completed.renderSupport(),
            completed.get(), RenderMode.asSet(Conlet.RenderMode.View)),
            portalSession);
    }
}
