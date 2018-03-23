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

'use strict';

var deMnlAhpAdmin = {
    l10n: {
    }
};

(function() {

    var l10n = deMnlAhpAdmin.l10n;
    
    $("body").on("click", ".AdHocPolling-admin-preview .CreatePoll-button",
            function(event) {
        let portletId = $(this).closest("[data-portlet-id]").attr("data-portlet-id");
        JGPortal.notifyPortletModel(portletId, "createPoll");
    })

    deMnlAhpAdmin.initView = function(pollGroups) {
        pollGroups.accordion({
            header: "> div > h3",
            heightStyle: "content"
        });
    }

    JGPortal.registerPortletMethod(
            "de.mnl.ahp.portlets.management.AdminPortlet",
            "updatePoll", updatePoll);

    let groupTemplate = $('<div class="pollGroup">'
            + '<h3></h3>'
            + '<div><table class="ui-widget"><thead class="ui-widget-header">'
            + '<tr><th>#1</th><th>#2</th><th>#3</th>'
            + '<th>#4</th><th>#5</th><th>#6</th></tr>'
            + '</thead><tbody class="ui-widget-content">'
            + '<tr><td>0</td><td>0</td><td>0</td><td>0</td><td>0</td><td>0</td></tr>'
            + '</tbody></table></div>'
            + '</div>');
    
    function updatePoll(portletId, params) {
        let pollData = params[0];
        
        // Update Preview
        let preview = JGPortal.findPortletPreview(portletId);
        if (preview) {
            let lastCreated = preview.find("div.lastCreated");
            let currentlyLast = lastCreated.data("startedAt");
            if (pollData.startedAt > currentlyLast) {
                preview.find("span.lastPollCreated").html(pollData.pollId);
                lastCreated.data("startedAt", pollData.startedAt);
            }
        }
        
        // Update View
        let view = JGPortal.findPortletView(portletId);
        if (!view) {
            return;
        }
        let pollGroups = view.find("div.pollGroups");
        let group = null;
        pollGroups.find(".pollGroup").each(function() {
            let poll = $(this);
            if (poll.attr("data-poll-id") == pollData.pollId) {
                group = poll;
                return false;
            }
        });
        if (group === null) {
            group = groupTemplate.clone();
            pollGroups.prepend(group);
            group.attr("data-poll-id", pollData.pollId);
            group.attr("data-started-at", pollData.startedAt);
            group.find("h3").html(pollData.pollId);
            group.find("table");
            pollGroups.accordion("refresh");
            pollGroups.accordion("option", "active", 0);
        }
        let cells = group.find("td");
        for (let i = 0; i < 6; i++) {
            $(cells[i]).html(pollData.counters[i]);
        }
    }
    
})();

