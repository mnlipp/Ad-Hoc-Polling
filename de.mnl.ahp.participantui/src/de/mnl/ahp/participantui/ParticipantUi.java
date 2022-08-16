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

import de.mnl.ahp.participantui.VotingController.State;
import de.mnl.ahp.service.events.GetPoll;
import de.mnl.ahp.service.events.GetPollCompleted;
import de.mnl.ahp.service.events.PollData;
import de.mnl.ahp.service.events.Vote;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jdrupes.httpcodec.protocols.http.HttpConstants.HttpStatus;
import org.jdrupes.httpcodec.protocols.http.HttpField;
import org.jdrupes.httpcodec.types.Converters;
import org.jdrupes.httpcodec.types.CookieList;
import org.jgrapes.core.Channel;
import org.jgrapes.core.ClassChannel;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.annotation.HandlerDefinition.ChannelReplacements;
import org.jgrapes.http.HttpRequestHandlerFactory;
import org.jgrapes.http.InMemorySessionManager;
import org.jgrapes.http.LanguageSelector.Selection;
import org.jgrapes.http.ResourcePattern;
import org.jgrapes.http.ResponseCreationSupport;
import org.jgrapes.http.Session;
import org.jgrapes.http.annotation.RequestHandler;
import org.jgrapes.http.events.DiscardSession;
import org.jgrapes.http.events.Request;
import org.jgrapes.http.freemarker.FreeMarkerRequestHandler;
import org.jgrapes.io.IOSubchannel;
import org.jgrapes.io.events.Close;
import org.jgrapes.io.events.Input;
import org.jgrapes.io.events.Purge;

/**
 *
 */
public class ParticipantUi extends FreeMarkerRequestHandler {

    private static Map<VotingController.State, String> stateToPage
        = Map.of(
            State.Authorized, "votePage.ftl.html",
            State.Voted, "byePage.ftl.html");

    private class AhpSvcChannel extends ClassChannel {
    }

    private Channel ahpSvcChannel;

    @SuppressWarnings("unchecked")
    public ParticipantUi(Channel componentChannel, Map<?, ?> properties) {
        super(componentChannel, ChannelReplacements.create().add(
            AhpSvcChannel.class, ((Map<String, Channel>) properties)
                .getOrDefault("AdHocPollingServiceChannel", componentChannel)),
            ParticipantUi.class.getClassLoader(),
            ParticipantUi.class.getPackage().getName().replace('.', '/'),
            ((Map<String, URI>) properties).getOrDefault(
                HttpRequestHandlerFactory.PREFIX, URI.create("/")));
        this.ahpSvcChannel = ((Map<String, Channel>) properties).getOrDefault(
            "AdHocPollingServiceChannel", componentChannel);
        // Because this handler provides a "top-level" page, we have to adapt
        // the
        // resource pattern.
        String stripped = prefix().getPath();
        stripped = stripped.substring(0, stripped.length() - 1);
        try {
            updatePrefixPattern(
                new ResourcePattern(stripped + "|," + stripped + "|**"));
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
        String pattern
            = (stripped.isEmpty() ? "/" : (stripped + "," + prefix()))
                + "," + prefix() + "static/**";
        RequestHandler.Evaluator.add(this, "onGet", pattern);
        RequestHandler.Evaluator.add(this, "onPost", pattern);
        attach(new InMemorySessionManager(componentChannel, pattern, 1100,
            stripped.isEmpty() ? "/" : stripped)).setIdName("id-voter");
    }

    @Override
    protected Map<String, Object> fmSessionModel(Optional<Session> session) {
        Map<String, Object> formModel = super.fmSessionModel(session);
        formModel.put("resourceUrl", new TemplateMethodModelEx() {
            @Override
            public Object exec(@SuppressWarnings("rawtypes") List arguments)
                    throws TemplateModelException {
                @SuppressWarnings("unchecked")
                List<TemplateModel> args = (List<TemplateModel>) arguments;
                if (!(args.get(0) instanceof SimpleScalar)) {
                    throw new TemplateModelException("Not a string.");
                }
                return ResponseCreationSupport.uriFromPath(prefix().getPath())
                    .resolve(((SimpleScalar) args.get(0)).getAsString())
                    .getRawPath();
            }
        });
        formModel.put("controller", session
            .flatMap(
                ses -> Optional.ofNullable(ses.get(VotingController.class)))
            .orElseGet(VotingController::new));
        return formModel;
    }

    /**
     * Provides the current page (according to the controller state) as
     * "top-level" resource and any additional static resources required
     * by this page.
     * 
     * @param event
     *            the event. The result will be set to `true` on success
     * @param channel
     *            the channel
     * @throws ParseException 
     */
    @RequestHandler(dynamic = true, priority = -100)
    public void onGet(Request.In.Get event, IOSubchannel channel) {
        prefixPattern().pathRemainder(event.requestUri()).ifPresent(path -> {
            boolean success;
            if (path.isEmpty()) {
                success = event.associatedGet(Session.class).map(session -> {
                    VotingController vc
                        = (VotingController) session.computeIfAbsent(
                            VotingController.class,
                            k -> new VotingController());
                    if (!sendProcessedTemplate(event, channel,
                        stateToPage.getOrDefault(vc.state(),
                            "authPage.ftl.html"))) {
                        return false;
                    }
                    if (vc.state() == State.Voted) {
                        vc.reset();
                        fire(new DiscardSession(session, channel));
                    }
                    return true;
                }).orElse(false);
            } else {
                success = ResponseCreationSupport.sendStaticContent(
                    event, channel,
                    requestPath -> ParticipantUi.class.getResource(path),
                    ResponseCreationSupport.DEFAULT_MAX_AGE_CALCULATOR);
            }
            event.setResult(true);
            event.stop();
            if (!success) {
                channel.respond(new Close());
            }
            channel.setAssociated(this, true);
        });
    }

    @RequestHandler(dynamic = true, priority = -100)
    public void onPost(Request.In.Post event, IOSubchannel channel) {
        // Associate the (pending) request and a form context with the channel
        channel.setAssociated(
            PostedUrlDataDecoder.class, new PostedUrlDataDecoder(event));
        event.setResult(true);
        event.stop();
        channel.setAssociated(this, false);
    }

    @Handler
    public void onInput(Input<ByteBuffer> event, IOSubchannel channel) {
        channel.associated(PostedUrlDataDecoder.class).ifPresent(dec -> {
            event.stop();
            dec.process(event).ifPresent(fields -> {
                // Request has been fully decoded, process
                dec.request().associatedGet(Session.class).map(session -> {
                    if (fields.containsKey("setLocale")) {
                        Selection selection
                            = (Selection) session.get(Selection.class);
                        if (selection != null) {
                            selection.prefer(Locale
                                .forLanguageTag(fields.get("setLocale")));
                        }
                        return true;
                    }
                    VotingController vc = (VotingController) session
                        .computeIfAbsent(VotingController.class,
                            k -> new VotingController());
                    if (fields.containsKey("submit_code")) {
                        try {
                            GetPoll checkEvent = new GetPoll(
                                Integer.parseInt(fields.get("code")));
                            checkEvent.setAssociated(IOSubchannel.class,
                                channel);
                            fire(checkEvent, ahpSvcChannel);
                            return false;
                        } catch (NumberFormatException e) {
                            return true;
                        }
                    }
                    if (fields.containsKey("chosen")) {
                        vc.voted();
                        fire(new Vote(vc.pollId(), Integer.parseInt(
                            fields.get("chosen")) - 1), ahpSvcChannel);
                        setVotedCookie(dec, vc);
                        return true;
                    }
                    return true;
                }).map(respond -> {
                    if (respond) {
                        dec.request().httpRequest().response().get().setField(
                            HttpField.LOCATION, prefix());
                        ResponseCreationSupport.sendResponse(
                            dec.request().httpRequest(), channel,
                            HttpStatus.SEE_OTHER);
                    }
                    return null;
                });
            });
        });
    }

    private void setVotedCookie(
            PostedUrlDataDecoder dec, VotingController vc) {
        HttpCookie votedCookie
            = new HttpCookie("ahp-voted-" + UUID.randomUUID(),
                vc.pollId() + "-" + vc.pollStartedAt().toEpochMilli());
        votedCookie.setPath("/");
        votedCookie.setMaxAge(
            Duration.between(Instant.now(), vc.pollExpiresAt()).toMillis()
                / 1000);
        dec.request().httpRequest().response().get()
            .computeIfAbsent(HttpField.SET_COOKIE, CookieList::new)
            .value().add(votedCookie);
    }

    @Handler
    public void onPurge(Purge event, IOSubchannel channel) {
        channel.associated(this, Boolean.class).filter(value -> value)
            .ifPresent(alwaysTrue -> {
                event.stop();
                channel.respond(new Close());
            });
    }

    @Handler(channels = AhpSvcChannel.class)
    public void onGetPollCompleted(GetPollCompleted event) {
        event.event().associated(IOSubchannel.class).ifPresent(channel -> {
            channel.associated(PostedUrlDataDecoder.class).ifPresent(dec -> {
                Request.In.Post request = dec.request();
                try {
                    processVote(event.event().get(), request);
                } catch (InterruptedException e) {
                    // Won't happen, event is completed
                }
                request.httpRequest().response().get().setField(
                    HttpField.LOCATION, prefix());
                ResponseCreationSupport.sendResponse(
                    request.httpRequest(), channel, HttpStatus.SEE_OTHER);
            });
        });
    }

    private void processVote(PollData pollData, Request.In request) {
        request.associatedGet(Session.class).ifPresent(session -> {
            VotingController vc = (VotingController) session
                .computeIfAbsent(VotingController.class,
                    k -> new VotingController());
            if (pollData == null) {
                vc.unknownPoll();
                return;
            }
            if (request.httpRequest().findValue(
                HttpField.COOKIE, Converters.COOKIE_LIST)
                .orElse(new CookieList()).stream().filter(
                    cookie -> cookie.getName().startsWith("ahp-voted-"))
                .map(HttpCookie::getValue).map(value -> {
                    String[] parts = value.split("-");
                    return Integer.parseInt(parts[0]) == pollData.pollId()
                        && Long.parseLong(parts[1]) == pollData.startedAt()
                            .toEpochMilli();
                }).filter(result -> result).findFirst().isPresent()) {
                vc.duplicateVote();
                return;
            }
            vc.authorized(pollData);
        });
    }
}
