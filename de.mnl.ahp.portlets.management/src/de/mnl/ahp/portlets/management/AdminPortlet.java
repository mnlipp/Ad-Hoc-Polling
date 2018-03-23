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

import de.mnl.ahp.service.events.CreatePoll;
import de.mnl.ahp.service.events.ListPolls;
import de.mnl.ahp.service.events.PollExpired;
import de.mnl.ahp.service.events.PollState;
import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateNotFoundException;

import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jdrupes.json.JsonObject;
import org.jgrapes.core.Channel;
import org.jgrapes.core.ClassChannel;
import org.jgrapes.core.Event;
import org.jgrapes.core.Manager;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.annotation.HandlerDefinition.ChannelReplacements;
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
import org.jgrapes.portal.events.NotifyPortletModel;
import org.jgrapes.portal.events.NotifyPortletView;
import org.jgrapes.portal.events.PortalReady;
import org.jgrapes.portal.events.RenderPortletRequest;
import org.jgrapes.portal.freemarker.FreeMarkerPortlet;

/**
 * A portlet for inspecting the services in an OSGi runtime.
 */
public class AdminPortlet extends FreeMarkerPortlet {

    private class AhpSvcChannel extends ClassChannel {
    }

    private static AtomicInteger instanceCounter = new AtomicInteger(0);
    private static final Set<RenderMode> MODES = RenderMode.asSet(
        DeleteablePreview, View);

    private Channel ahpSvcChannel;
    private AdminModel adminModel;

    /**
     * Creates a new component with its channel set to the given channel.
     * 
     * @param componentChannel the channel that the component's handlers listen
     *            on by default and that {@link Manager#fire(Event, Channel...)}
     *            sends the event to
     */
    public AdminPortlet(Channel componentChannel, Channel ahpSvcChannel) {
        super(componentChannel, ChannelReplacements.create().add(
            AhpSvcChannel.class, ahpSvcChannel), true);
        this.ahpSvcChannel = ahpSvcChannel;
        adminModel = new AdminModel();
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
                .setRequires(new String[] { "chartjs.org" })
                .setScriptUri(event.renderSupport().portletResource(
                    type(), "Admin-functions.ftl.js")))
            .addCss(event.renderSupport(),
                PortalWeblet.uriFromPath("Admin-style.css"))
            .setInstantiable());
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jgrapes.portal.AbstractPortlet#generatePortletId()
     */
    @Override
    protected String generatePortletId() {
        return adminModel.getPortletId();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jgrapes.portal.AbstractPortlet#modelFromSession
     */
    @SuppressWarnings("unchecked")
    @Override
    protected <T extends Serializable> Optional<T> stateFromSession(
            Session session, String portletId, Class<T> type) {
        if (portletId.startsWith(type() + "-")) {
            return Optional.of((T) adminModel);
        }
        return Optional.empty();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jgrapes.portal.AbstractPortlet#doAddPortlet
     */
    protected String doAddPortlet(AddPortletRequest event,
            PortalSession channel) throws Exception {
        Template tpl = freemarkerConfig().getTemplate("Admin-preview.ftl.html");
        channel.respond(new RenderPortletFromTemplate(event,
            AdminPortlet.class, adminModel.getPortletId(),
            tpl, fmModel(event, channel, adminModel))
                .setRenderMode(DeleteablePreview).setSupportedModes(MODES)
                .setForeground(true));
        return adminModel.getPortletId();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jgrapes.portal.AbstractPortlet#doRenderPortlet
     */
    @Override
    protected void doRenderPortlet(RenderPortletRequest event,
            PortalSession channel, String portletId,
            Serializable retrievedState)
            throws Exception {
        AdminModel portletModel = (AdminModel) retrievedState;
        switch (event.renderMode()) {
        case Preview:
        case DeleteablePreview: {
            Template tpl
                = freemarkerConfig().getTemplate("Admin-preview.ftl.html");
            channel.respond(new RenderPortletFromTemplate(event,
                AdminPortlet.class, portletId,
                tpl, fmModel(event, channel, portletModel))
                    .setRenderMode(DeleteablePreview).setSupportedModes(MODES)
                    .setForeground(event.isForeground()));
            fire(new ListPolls(channel.browserSession().id()), ahpSvcChannel);
            break;
        }
        case View: {
            Template tpl
                = freemarkerConfig().getTemplate("Admin-view.ftl.html");
            channel.respond(new RenderPortletFromTemplate(event,
                AdminPortlet.class, portletModel.getPortletId(),
                tpl, fmModel(event, channel, portletModel))
                    .setSupportedModes(MODES)
                    .setForeground(event.isForeground()));
            fire(new ListPolls(channel.browserSession().id()), ahpSvcChannel);
            break;
        }
        default:
            break;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jgrapes.portal.AbstractPortlet#doDeletePortlet
     */
    @Override
    protected void doDeletePortlet(DeletePortletRequest event,
            PortalSession channel,
            String portletId, Serializable portletState) throws Exception {
        channel.respond(new DeletePortlet(portletId));
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jgrapes.portal.AbstractPortlet#doNotifyPortletModel
     */
    @Override
    protected void doNotifyPortletModel(NotifyPortletModel event,
            PortalSession channel, Serializable portletState)
            throws Exception {
        event.stop();

        if (event.method().equals("createPoll")) {
            fire(new CreatePoll(channel.browserSession().id()), ahpSvcChannel);
            return;
        }
    }

    @Handler(channels = AhpSvcChannel.class)
    public void onPollState(PollState event) throws IOException {
        JsonObject json = JsonObject.create();
        json.setField("pollId", event.pollData().pollId())
            .setField("startedAt", event.pollData().startedAt().toEpochMilli())
            .setField("counters", event.pollData().counter());
        for (PortalSession ps : trackedSessions()) {
            if (!ps.browserSession().id().equals(event.pollData().adminId())) {
                continue;
            }
            ps.respond(new NotifyPortletView(type(), adminModel.getPortletId(),
                "updatePoll", json));
        }
    }

    @Handler(channels = AhpSvcChannel.class)
    public void onPollExpired(PollExpired event) throws IOException {
        for (PortalSession ps : trackedSessions()) {
            if (!ps.browserSession().id().equals(event.adminId())) {
                continue;
            }
            ps.respond(new NotifyPortletView(type(), adminModel.getPortletId(),
                "pollExpired", event.pollId()));
        }
    }

    public class AdminModel extends PortletBaseModel {
        private static final long serialVersionUID = -7400194644538987104L;

        public AdminModel() {
            super(type() + "-" + instanceCounter.getAndIncrement());
        }

    }
}
