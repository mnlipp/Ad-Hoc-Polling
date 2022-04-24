/*
 * Ad Hoc Polling Application
 * Copyright (C) 2022 Michael N. Lipp
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

import { reactive, ref, createApp, computed, onMounted } from "vue";
import JGConsole from "jgconsole"
import JgwcPlugin, { JGWC } from "jgwc";
import { provideApi, getApi } from "aash-plugin";
import l10nBundles from "l10nBundles";
import BarGraph from "./BarGraph"

// For global access
declare global {
    interface Window {
        deMnlAhpAdmin: any;
    }
}

window.deMnlAhpAdmin = {}

interface PollData {
    pollId: number;
    startedAt: number;
    counters: number[];
}

let polls = reactive(new Array<PollData>());

JGConsole.registerConletFunction(
    "de.mnl.ahp.conlets.management.AdminConlet",
    "updatePoll", function(conletId: string, pollData: PollData) {
        for(let idx = 0; idx < polls.length + 1; idx++) {
            if (idx >= polls.length 
                || pollData.startedAt > polls.at(idx).startedAt) {
                polls.splice(idx, 0, pollData);
                return;
            }
            if (polls.at(idx).pollId == pollData.pollId) {
                for (let i = 0; i < pollData.counters.length; i++) {
                    polls.at(idx).counters[i] = pollData.counters[i];
                }
                return;
            }
        }
    });

JGConsole.registerConletFunction(
    "de.mnl.ahp.conlets.management.AdminConlet",
    "pollExpired", function(conletId, pollId) {
        for(let idx = 0; idx < polls.length; idx++) {
            if (polls.at(idx).pollId == pollId) {
                polls.splice(idx, 1);
                return;
            }
        }
    });

window.deMnlAhpAdmin.initPreview = function(previewDom: HTMLElement) {
    let app = createApp({
        setup() {
            const lastPollCreated = computed(() => {
                if (polls.length == 0) {
                    return "";
                }
                return polls.at(0).pollId.toString();
            });
            
            const createPoll = (event: Event) => {
                let conletId = previewDom
                    .closest("[data-conlet-id]")!.getAttribute("data-conlet-id")!;
                JGConsole.notifyConletModel(conletId, "createPoll");
            };
            
            const localize = (key: string) => {
                return JGConsole.localize(
                    l10nBundles, JGWC.lang()!, key);
            };
                        
            return { lastPollCreated, createPoll, localize };
        }
    });
    app.use(JgwcPlugin);
    app.mount(previewDom);
}

window.deMnlAhpAdmin.initView = function(viewDom: HTMLElement) {
    let app = createApp({
        components: {
            BarGraph
        },
        
        setup() {
            const localize = (key: string) => {
                return JGConsole.localize(
                    l10nBundles, JGWC.lang()!, key);
            };
            
            const simpleTimeFormatter = new Intl.DateTimeFormat(
                JGWC.lang() || 'en',
                { hour: 'numeric', minute: 'numeric', second: 'numeric' });
            
            const formatTime = (value: number) => {
                return simpleTimeFormatter.format(new Date(value));                
            };
            
            const total = (values: number[]) => {
                let sum = 0;
                for (let value of values) {
                    sum += value;
                }
                return sum;
            }
            
            return { polls, localize, formatTime, total };
        }
    });
    app.use(JgwcPlugin);
    app.mount(viewDom);
}



/*

(function() {

    var l10n = deMnlAhpAdmin.l10n;
    var pollGroupCounter = 0;
    
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
    
    function updatePoll(conletId, pollData) {
        // Update Preview
        let preview = JGConsole.findConletPreview(conletId);
        if (preview) {
            preview = $(preview);
            let lastCreated = preview.find("div.lastCreated");
            let currentlyLast = lastCreated.data("startedAt");
            if (pollData.startedAt > currentlyLast) {
                preview.find("span.lastPollCreated").html(pollData.pollId);
                lastCreated.data("startedAt", pollData.startedAt);
                lastCreated.data("pollId", pollData.pollId);
            }
        }
        
        // Update View
        let view = JGConsole.findConletView(conletId);
        if (!view) {
            return;
        }
        view = $(view);
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
                + '${_("started at")}' 
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
    
    JGConsole.registerConletFunction(
            "de.mnl.ahp.conlets.management.AdminConlet",
            "pollExpired", pollExpired);

    function pollExpired(conletId, params) {
        let pollId = params[0];
        
        // Update Preview
        let preview = JGConsole.findConletPreview(conletId);
        if (preview) {
            let lastCreated = preview.find("div.lastCreated");
            if (lastCreated.data("pollId") === pollId) {
                preview.find("span.lastPollCreated").html("");
            }
        }
        
        // Update View
        let view = JGConsole.findConletView(conletId);
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

*/