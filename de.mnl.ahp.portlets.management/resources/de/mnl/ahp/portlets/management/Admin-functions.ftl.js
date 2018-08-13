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
    var pollGroupCounter = 0;
    
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

    let groupTemplate = $('<div class="card pollGroup">'
            + '<div class="card-header">'
            + '<button class="btn btn-link" data-toggle="collapse">'
            + '<h3></h3></button></div>'
            + '<div class="collapse" data-parent=".pollGroups">'
            + '<div class="card-body"><div class="table-wrapper"><table>'
            + '<tr><th class="ui-widget-header">#1</th><td>0</td></tr>'
            + '<tr><th class="ui-widget-header">#2</th><td>0</td></tr>'
            + '<tr><th class="ui-widget-header">#3</th><td>0</td></tr>'
            + '<tr><th class="ui-widget-header">#4</th><td>0</td></tr>'
            + '<tr><th class="ui-widget-header">#5</th><td>0</td></tr>'
            + '<tr><th class="ui-widget-header">#6</th><td>0</td></tr>'
            + '<tr><th class="ui-widget-header">${_("Total")}</th><td>0</td></tr>'
            + '</table></div>'
            + '<div class="chart-wrapper"><canvas class="chart"></canvas></div>'
            + '</div></div></div>');
    
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
                lastCreated.data("pollId", pollData.pollId);
            }
        }
        
        // Update View
        let view = JGPortal.findPortletView(portletId);
        if (!view) {
            return;
        }
        let lang = view.closest('[lang]').attr('lang') || 'en';
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
            group.find("button").attr("data-target", "#pollGroupBody" + pollGroupCounter);
            group.find(".collapse").attr("id", "pollGroupBody" + pollGroupCounter);
            pollGroupCounter += 1;
            let inserted = false;
            pollGroups.find(".pollGroup").each(function() {
                if (pollData.startedAt > parseInt($(this).attr("data-started-at"))) {
                    group.insertBefore($(this));
                    inserted = true;
                    return false;
                }
            });
            if (!inserted) {
                pollGroups.append(group);
            }
            group.attr("data-poll-id", pollData.pollId);
            group.attr("data-started-at", pollData.startedAt);
            group.find("h3").html("<b>" + pollData.pollId + "</b> (" 
                + "${_("started at")}" 
                + ": " + moment(pollData.startedAt).locale(lang).format("LTS") 
                + ")");
            createChart(group.find("canvas"));
            pollGroups.find(".pollGroup").first().find(".collapse").collapse("show");
        }
        let cells = group.find("td");
        let sum = 0;
        for (let i = 0; i < 6; i++) {
            $(cells[i]).html(pollData.counters[i]);
            sum += pollData.counters[i];
        }
        $(cells[6]).html(sum);
        let chart = group.find("canvas").data("chartjs-chart");
        chart.data.datasets[0].data = pollData.counters;
        chart.update(0);
    }
    
    function createChart(chartCanvas) {
        let ctx = chartCanvas[0].getContext('2d');
        let chart = new Chart(ctx, {
            // The type of chart we want to create
            type: 'bar',
            data: {
                labels: ['#1', '#2', '#3', '#4', '#5', '#6'],
                datasets: [{
                    label: '${_("Votes")}',
                    backgroundColor: 'rgba(0, 255, 0, 0.7)',
                    data: [0, 1, 2, 3, 4, 5]
                }]
            },
            options: {
                legend: {
                    display: false
                },
                scales: {
                    yAxes: [{
                        ticks: {
                            min: 0
                        }
                    }]
                }
            }
        });
        chartCanvas.data('chartjs-chart', chart);
    }
    
    JGPortal.registerPortletMethod(
            "de.mnl.ahp.portlets.management.AdminPortlet",
            "pollExpired", pollExpired);

    function pollExpired(portletId, params) {
        let pollId = params[0];
        
        // Update Preview
        let preview = JGPortal.findPortletPreview(portletId);
        if (preview) {
            let lastCreated = preview.find("div.lastCreated");
            if (lastCreated.data("pollId") === pollId) {
                preview.find("span.lastPollCreated").html("");
            }
        }
        
        // Update View
        let view = JGPortal.findPortletView(portletId);
        if (!view) {
            return;
        }
        let pollGroups = view.find("div.pollGroups");
        pollGroups.find(".pollGroup").each(function() {
            let poll = $(this);
            if (poll.attr("data-poll-id") == pollId) {
                poll.remove();
                return false;
            }
        });
    }

})();

