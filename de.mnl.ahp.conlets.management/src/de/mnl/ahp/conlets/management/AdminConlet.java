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

package de.mnl.ahp.conlets.management;

import de.mnl.ahp.service.events.CreatePoll;
import de.mnl.ahp.service.events.ListPolls;
import de.mnl.ahp.service.events.PollExpired;
import de.mnl.ahp.service.events.PollState;
import freemarker.core.ParseException;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.jdrupes.json.JsonObject;
import org.jgrapes.core.Channel;
import org.jgrapes.core.ClassChannel;
import org.jgrapes.core.Event;
import org.jgrapes.core.Manager;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.annotation.HandlerDefinition.ChannelReplacements;
import org.jgrapes.webconsole.base.Conlet.RenderMode;
import org.jgrapes.webconsole.base.ConletBaseModel;
import org.jgrapes.webconsole.base.ConsoleSession;
import org.jgrapes.webconsole.base.WebConsoleUtils;
import org.jgrapes.webconsole.base.events.AddConletType;
import org.jgrapes.webconsole.base.events.AddPageResources.ScriptResource;
import org.jgrapes.webconsole.base.events.ConsoleReady;
import org.jgrapes.webconsole.base.events.NotifyConletModel;
import org.jgrapes.webconsole.base.events.NotifyConletView;
import org.jgrapes.webconsole.base.events.RenderConlet;
import org.jgrapes.webconsole.base.events.RenderConletRequestBase;
import org.jgrapes.webconsole.base.events.SetLocale;
import org.jgrapes.webconsole.base.freemarker.FreeMarkerConlet;

/**
 * A conlet for poll administration.
 */
public class AdminConlet extends FreeMarkerConlet<AdminConlet.AdminModel> {

    private class AhpSvcChannel extends ClassChannel {
    }

    /** Boolean property that controls if the preview is deletable. */
    public static final String DELETABLE = "Deletable";
    private static final Set<RenderMode> MODES = RenderMode.asSet(
        RenderMode.Preview, RenderMode.StickyPreview, RenderMode.View);

    private Channel ahpSvcChannel;

    /**
     * Creates a new component with its channel set to the given channel.
     * 
     * @param componentChannel the channel that the component's handlers listen
     *            on by default and that {@link Manager#fire(Event, Channel...)}
     *            sends the event to
     */
    public AdminConlet(Channel componentChannel, Channel ahpSvcChannel) {
        super(componentChannel, ChannelReplacements.create().add(
            AhpSvcChannel.class, ahpSvcChannel));
        this.ahpSvcChannel = ahpSvcChannel;
    }

    @Handler
    public void onConsoleReady(ConsoleReady event, ConsoleSession channel)
            throws TemplateNotFoundException, MalformedTemplateNameException,
            ParseException, IOException {
        // Add conlet resources to page
        channel.respond(new AddConletType(type())
            .addRenderMode(RenderMode.Preview).setDisplayNames(
                localizations(channel.supportedLocales(), "conletName"))
            .addScript(new ScriptResource()
                .setScriptUri(event.renderSupport().conletResource(
                    type(), "Admin-functions.js"))
                .setScriptType("module"))
            .addCss(event.renderSupport(),
                WebConsoleUtils.uriFromPath("Admin-style.css")));
    }

    @Override
    protected Optional<AdminModel> createStateRepresentation(
            RenderConletRequestBase<?> event,
            ConsoleSession channel, String conletId) throws IOException {
        return Optional.of(new AdminModel(conletId));
    }

    @Override
    protected Set<RenderMode> doRenderConlet(RenderConletRequestBase<?> event,
            ConsoleSession consoleSession, String conletId,
            AdminModel conletState) throws Exception {
        Set<RenderMode> renderedAs = new HashSet<>(event.renderAs());
        if (event.renderAs().contains(RenderMode.Preview)) {
            Template tpl
                = freemarkerConfig().getTemplate("Admin-preview.ftl.html");
            consoleSession.respond(new RenderConlet(type(), conletId,
                processTemplate(event, tpl,
                    fmModel(event, consoleSession, conletId, conletState)))
                        .setRenderAs(
                            RenderMode.Preview.addModifiers(event.renderAs()))
                        .setSupportedModes(MODES));
            renderedAs.add(RenderMode.Preview);
            fire(new ListPolls(
                consoleSession.browserSession().id()), ahpSvcChannel);
        }
        if (event.renderAs().contains(RenderMode.View)) {
            Template tpl
                = freemarkerConfig().getTemplate("Admin-view.ftl.html");
            consoleSession.respond(new RenderConlet(type(), conletId,
                processTemplate(event, tpl,
                    fmModel(event, consoleSession, conletId, conletState)))
                        .setRenderAs(
                            RenderMode.View.addModifiers(event.renderAs()))
                        .setSupportedModes(MODES));
            renderedAs.add(RenderMode.View);
            fire(new ListPolls(
                consoleSession.browserSession().id()), ahpSvcChannel);
        }
        return renderedAs;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jgrapes.portal.AbstractPortlet#doNotifyPortletModel
     */
    @Override
    protected void doUpdateConletState(NotifyConletModel event,
            ConsoleSession channel, AdminModel conletModel)
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
        for (ConsoleSession ps : trackedSessions()) {
            if (!ps.browserSession().id().equals(event.pollData().adminId())) {
                continue;
            }
            for (String conletId : conletIds(ps)) {
                ps.respond(new NotifyConletView(type(), conletId,
                    "updatePoll", json));
            }
        }
    }

    @Handler(channels = AhpSvcChannel.class)
    public void onPollExpired(PollExpired event) throws IOException {
        for (ConsoleSession ps : trackedSessions()) {
            if (!ps.browserSession().id().equals(event.adminId())) {
                continue;
            }
            for (String conletId : conletIds(ps)) {
                ps.respond(new NotifyConletView(type(), conletId,
                    "pollExpired", event.pollId()));
            }
        }
    }

    @Override
    protected boolean doSetLocale(SetLocale event, ConsoleSession channel,
            String conletId) throws Exception {
        return true;
    }

    public class AdminModel extends ConletBaseModel {
        private static final long serialVersionUID = -7400194644538987104L;

        public AdminModel(String conletId) {
            super(conletId);
        }

    }
}
