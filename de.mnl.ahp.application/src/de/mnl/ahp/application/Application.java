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

import de.mnl.ahp.service.AdHocPollingService;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.ResourceBundle;

import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.NamedChannel;
import org.jgrapes.core.events.Stop;
import org.jgrapes.http.HttpRequestHandlerFactory;
import org.jgrapes.http.HttpServer;
import org.jgrapes.http.InMemorySessionManager;
import org.jgrapes.http.LanguageSelector;
import org.jgrapes.http.events.GetRequest;
import org.jgrapes.http.events.PostRequest;
import org.jgrapes.io.FileStorage;
import org.jgrapes.io.NioDispatcher;
import org.jgrapes.io.util.PermitsPool;
import org.jgrapes.net.TcpServer;
import org.jgrapes.osgi.core.ComponentCollector;
import org.jgrapes.portal.base.KVStoreBasedPortalPolicy;
import org.jgrapes.portal.base.PageResourceProviderFactory;
import org.jgrapes.portal.base.Portal;
import org.jgrapes.portal.base.PortalLocalBackedKVStore;
import org.jgrapes.portal.base.PortalWeblet;
import org.jgrapes.portal.base.PortletComponentFactory;
import org.jgrapes.portal.bootstrap4.Bootstrap4Weblet;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 *
 */
public class Application extends Component implements BundleActivator {

	private Application app;
	
	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		app = new Application();
		// Attach a general nio dispatcher
		app.attach(new NioDispatcher());
		
		// The central service
 		app.attach(new AdHocPollingService(app.channel(), context));
		
		// Create a TCP server listening on port 5001
		Channel tcpChannel = new NamedChannel("TCP");
		app.attach(new TcpServer(tcpChannel)
            .setServerAddress(new InetSocketAddress(
                Optional.ofNullable(System.getenv("PORT"))
                    .map(Integer::parseInt).orElse(5001)))
            .setConnectionLimiter(new PermitsPool(300))
            .setMinimalPurgeableTime(1000)
        );

		// Create an HTTP server as converter between transport and application
		// layer.
		Channel httpChannel = new NamedChannel("HTTP");
		HttpServer httpServer = app.attach(new HttpServer(httpChannel, 
		        tcpChannel, GetRequest.class, PostRequest.class));
		
		// Build HTTP application layer
		httpServer.attach(new InMemorySessionManager(httpChannel));
		httpServer.attach(new LanguageSelector(httpChannel));
		httpServer.attach(new FileStorage(httpChannel, 65536));
		httpServer.attach(new ComponentCollector<>(
				httpChannel, context, HttpRequestHandlerFactory.class, 
				type -> {
					switch(type) {
					case "de.mnl.ahp.participantui.ParticipantUi":
                    return Arrays.asList(Components.mapOf(
                        HttpRequestHandlerFactory.PREFIX, URI.create("/"),
                        "AdHocPollingServiceChannel", app.channel()));
					default:
						return Arrays.asList(Collections.emptyMap());
					}
				}));
        PortalWeblet portalWeblet
            = httpServer.attach(new Bootstrap4Weblet(httpChannel, Channel.SELF,
                new URI("/admin")))
                .prependClassTemplateLoader(this.getClass())
                .setResourceBundleSupplier(l -> ResourceBundle.getBundle(
                    getClass().getPackage().getName() + ".portal-l10n", l,
                    ResourceBundle.Control.getNoFallbackControl(
                        ResourceBundle.Control.FORMAT_DEFAULT)))
                .setPortalSessionInactivityTimeout(300000);
        Portal portal = portalWeblet.portal();
        portal.attach(new PortalLocalBackedKVStore(
            portal, portalWeblet.prefix().getPath()));
		portal.attach(new KVStoreBasedPortalPolicy(portal));
        portal.attach(new NewPortalSessionPolicy(portal));
        portal.attach(new ActionFilter(portal));
		portal.attach(new ComponentCollector<>(
				portal, context, PageResourceProviderFactory.class));
		portal.attach(new ComponentCollector<>(
				portal, context, PortletComponentFactory.class,
				type -> {
					switch(type) {
					case "de.mnl.ahp.portlets.management.AdminPortlet":
						return Arrays.asList(Components.mapOf(
								"AdHocPollingServiceChannel", app.channel()));
					default:
						return Arrays.asList(Collections.emptyMap());
					}
				}));
		Components.start(app);
	}

	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
		app.fire(new Stop(), Channel.BROADCAST);
		Components.awaitExhaustion();
	}
}
