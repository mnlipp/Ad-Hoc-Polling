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
import de.mnl.ahp.service.events.CheckPoll;
import de.mnl.ahp.service.events.PollChecked;
import de.mnl.ahp.service.events.Vote;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

import java.net.URI;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jdrupes.httpcodec.protocols.http.HttpConstants.HttpStatus;
import org.jdrupes.httpcodec.protocols.http.HttpField;
import org.jgrapes.core.Channel;
import org.jgrapes.core.ClassChannel;
import org.jgrapes.core.Components;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.annotation.HandlerDefinition.ChannelReplacements;
import org.jgrapes.http.HttpRequestHandlerFactory;
import org.jgrapes.http.LanguageSelector.Selection;
import org.jgrapes.http.ResourcePattern;
import org.jgrapes.http.ResponseCreationSupport;
import org.jgrapes.http.Session;
import org.jgrapes.http.annotation.RequestHandler;
import org.jgrapes.http.events.GetRequest;
import org.jgrapes.http.events.PostRequest;
import org.jgrapes.http.events.Request;
import org.jgrapes.http.freemarker.FreeMarkerRequestHandler;
import org.jgrapes.io.IOSubchannel;
import org.jgrapes.io.events.Close;
import org.jgrapes.io.events.Input;

/**
 *
 */
public class ParticipantUi extends FreeMarkerRequestHandler {

    private static Map<VotingController.State, String> stateToPage
        = Components.mapOf(
            State.Authorized, "votePage.ftl.html",
            State.Voted, "byePage.ftl.html");

    private class AhpSvcChannel extends ClassChannel {
    }

    private Channel ahpSvcChannel;

    public ParticipantUi(Channel componentChannel,
            Map<Object, Object> properties) {
        super(componentChannel, ChannelReplacements.create().add(
            AhpSvcChannel.class, (Channel) properties.getOrDefault(
                "AdHocPollingServiceChannel", componentChannel)),
            ParticipantUi.class.getClassLoader(),
            ParticipantUi.class.getPackage().getName().replace('.', '/'),
            (URI) properties.getOrDefault(
                HttpRequestHandlerFactory.PREFIX, ""));
        this.ahpSvcChannel = (Channel) properties.getOrDefault(
            "AdHocPollingServiceChannel", componentChannel);
		// Because this handler provides a "top-level" page, we have to adapt the
		// resource pattern.
		String prefix = prefix().getPath();
		prefix = prefix.substring(0, prefix.length() - 1);
		try {
			updatePrefixPattern(new ResourcePattern(prefix + "|," + prefix + "|**"));
		} catch (ParseException e) {
			throw new IllegalArgumentException(e);
		}
		String pattern = prefix + "," + prefix + "/**" + "," + prefix + "/static/**";
		RequestHandler.Evaluator.add(this, "onGet", pattern);
		RequestHandler.Evaluator.add(this, "onPost", pattern);
	}

	@Override
	protected Map<String, Object> fmSessionModel(Optional<Session> session) {
		Map<String, Object> portalModel = super.fmSessionModel(session);
		portalModel.put("resourceUrl", new TemplateMethodModelEx() {
			@Override
			public Object exec(@SuppressWarnings("rawtypes") List arguments)
					throws TemplateModelException {
				@SuppressWarnings("unchecked")
				List<TemplateModel> args = (List<TemplateModel>)arguments;
				if (!(args.get(0) instanceof SimpleScalar)) {
					throw new TemplateModelException("Not a string.");
				}
				return ResponseCreationSupport.uriFromPath(prefix() + "/").resolve(
						((SimpleScalar)args.get(0)).getAsString()).getRawPath();
			}
		});
		return portalModel;
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
	@RequestHandler(dynamic=true, priority=-100)
	public void onGet(GetRequest event, IOSubchannel channel) {
		prefixPattern().pathRemainder(event.requestUri()).ifPresent(path -> {
			boolean success;
			if (path.isEmpty()) {
				Optional<Session> session = event.associated(Session.class);
				VotingController vc = (VotingController)session.get().computeIfAbsent(
						VotingController.class, k -> new VotingController());
                success = sendProcessedTemplate(event, channel,
                    stateToPage.getOrDefault(vc.state(), "authPage.ftl.html"));
                if (vc.state() == State.Voted) {
                    vc.reset();
                }
			} else {
				success = ResponseCreationSupport.sendStaticContent(
						event, channel, requestPath -> ParticipantUi.class.getResource(path), 
						ResponseCreationSupport.DEFAULT_MAX_AGE_CALCULATOR);
			}
			event.setResult(true);
			event.stop();
			if (!success) {
				channel.respond(new Close());
			}
		});
	}

	@RequestHandler(dynamic=true, priority=-100)
	public void onPost(PostRequest event, IOSubchannel channel) {
		// Associate the (pending) request and a form context with the channel
		channel.setAssociated(
				PostedUrlDataDecoder.class, new PostedUrlDataDecoder(event));
		event.setResult(true);
		event.stop();
	}
	
	@Handler
	public void onInput(Input<ByteBuffer> event, IOSubchannel channel) {
		channel.associated(PostedUrlDataDecoder.class).ifPresent(dec -> {
			event.stop();
			dec.process(event).ifPresent(fields -> {
                // Request has been fully decoded, process
                dec.request().associated(Session.class).map(session -> {
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
                        CheckPoll checkEvent = new CheckPoll(
                            Integer.parseInt(fields.get("code")));
                        checkEvent.setAssociated(IOSubchannel.class, channel);
                        fire(checkEvent, ahpSvcChannel);
                        return false;
                    }
                    if (fields.containsKey("chosen")) {
                        vc.voted();
                        fire(new Vote(vc.pollId(), Integer.parseInt(
                            fields.get("chosen")) - 1), ahpSvcChannel);
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

    @Handler(channels = AhpSvcChannel.class)
    public void onPollChecked(PollChecked event) {
        event.event().associated(IOSubchannel.class).ifPresent(channel -> {
            channel.associated(PostedUrlDataDecoder.class).ifPresent(dec -> {
                Request request = dec.request();
                request.associated(Session.class).ifPresent(session -> {
                    VotingController vc = (VotingController) session
                        .computeIfAbsent(VotingController.class,
                            k -> new VotingController());
                    try {
                        if (event.event().get() != null
                            && event.event().get()) {
                            vc.authorized(event.event().pollId());
                        }
                    } catch (InterruptedException e) {
                        // Is completed, cannot happen
                    }
                    ;
                });
                request.httpRequest().response().get().setField(
                    HttpField.LOCATION, prefix());
                ResponseCreationSupport.sendResponse(
                    request.httpRequest(), channel, HttpStatus.SEE_OTHER);
            });
        });
    }
}
