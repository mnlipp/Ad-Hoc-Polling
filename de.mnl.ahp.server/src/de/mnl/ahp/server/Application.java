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

package de.mnl.ahp.server;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
import org.jgrapes.net.TcpServer;
import org.jgrapes.osgi.core.ComponentCollector;
import org.jgrapes.portal.KVStoreBasedPortalPolicy;
import org.jgrapes.portal.PageResourceProviderFactory;
import org.jgrapes.portal.Portal;
import org.jgrapes.portal.PortalLocalBackedKVStore;
import org.jgrapes.portal.PortletComponentFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 *
 */
public class Application extends Component implements BundleActivator {

	private static BundleContext context;
	private Application app;
	
	public static BundleContext context() {
		return context;
	}
	
	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		Application.context = context;
		// The demo component is the application
		app = new Application();
		// Attach a general nio dispatcher
		app.attach(new NioDispatcher());

		// Network level unencrypted channel.
		Channel httpTransport = new NamedChannel("httpTransport");
		// Create a TCP server listening on port 5001
		app.attach(new TcpServer(httpTransport)
				.setServerAddress(new InetSocketAddress(
						Optional.ofNullable(System.getenv("PORT"))
						.map(Integer::parseInt).orElse(5001))));

		// Create an HTTP server as converter between transport and application
		// layer.
		app.attach(new HttpServer(app, 
		        httpTransport, GetRequest.class, PostRequest.class));
		
		// Build application layer
		app.attach(new InMemorySessionManager(app.channel()));
		app.attach(new LanguageSelector(app.channel()));
		app.attach(new FileStorage(app.channel(), 65536));
		app.attach(new ComponentCollector<>(
				app.channel(), context, HttpRequestHandlerFactory.class, 
				type -> {
					switch(type) {
					case "de.mnl.ahp.participantui.ParticipantUi":
						Map<Object,Object> map = new HashMap<>();
						map.put(HttpRequestHandlerFactory.PREFIX, URI.create("/test/"));
						return Arrays.asList(map);
					default:
						return Arrays.asList(Collections.emptyMap());
					}
				}));
		Portal portal = app.attach(new Portal(Channel.SELF, app.channel(), 
				new URI("/admin")))
				.setResourceBundleSupplier(l -> ResourceBundle.getBundle(
					getClass().getPackage().getName() + ".portal-l10n", l,
					ResourceBundle.Control.getNoFallbackControl(
							ResourceBundle.Control.FORMAT_DEFAULT)))
				.setFallbackResourceSupplier((themeProvider, resource) -> {
					return Application.class.getResource(resource);
				})
				.setPortalSessionInactivityTimeout(300000);
		portal.attach(new PortalLocalBackedKVStore(
				portal, portal.prefix().getPath()));
		portal.attach(new KVStoreBasedPortalPolicy(portal));
		portal.attach(new NewPortalSessionPolicy(portal));
		portal.attach(new ComponentCollector<>(
				portal, context, PageResourceProviderFactory.class));
		portal.attach(new ComponentCollector<>(
				portal, context, PortletComponentFactory.class));
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
