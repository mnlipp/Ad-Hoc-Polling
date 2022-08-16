/*
 * JGrapes Event Driven Framework
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

package de.mnl.ahp.application;

import java.util.ResourceBundle;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.webconsole.base.ConsoleConnection;
import org.jgrapes.webconsole.base.events.DisplayNotification;
import org.jgrapes.webconsole.base.events.NotifyConletModel;

/**
 *
 */
public class ActionFilter extends Component {

    public ActionFilter(Channel componentChannel) {
        super(componentChannel);
    }

    @Handler(priority = 1000)
    public void onNotifyPortletModel(NotifyConletModel event,
            ConsoleConnection channel) {
        if (event.conletId()
            .startsWith("org.jgrapes.osgi.webconlet.bundles.BundleListConlet~")
            && !event.method().equals("sendDetails")) {
            event.stop();
            ResourceBundle resources = ResourceBundle.getBundle(
                ActionFilter.class.getPackage().getName() + ".app-l10n");
            channel.respond(new DisplayNotification("<span>"
                + resources.getString("actionDisabled")
                + "</span>")
                    .addOption("autoClose", 5000));
        }
    }
}
