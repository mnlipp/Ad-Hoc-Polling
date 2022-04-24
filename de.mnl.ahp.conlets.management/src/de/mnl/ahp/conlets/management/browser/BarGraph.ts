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

import { defineComponent, reactive, ref, createApp, computed, 
    onMounted, h, watch } from "vue";
import { Chart } from "chartjs"; 
import JGConsole from "jgconsole"
import JgwcPlugin, { JGWC } from "jgwc";
import l10nBundles from "l10nBundles";

export default defineComponent({
    props: {
        data: { type: Array }
    },
    
    setup(props) {
        
        const localize = (key: string) => {
            return JGConsole.localize(
                l10nBundles, JGWC.lang()!, key);
        };

        let chart: Chart<any, any, any> | null = null;

        watch(props, (newProps, oldProps) => {
            if (chart) {
                chart.data.datasets[0].data = props.data;
                chart.update();
            }
        });

        const canvas = ref(null);
        onMounted(() => {
            let ctx = canvas.value.getContext('2d');
            chart = new Chart(ctx, {
                // The type of chart we want to create
                type: 'bar',
                data: {
                    labels: ['#1', '#2', '#3', '#4', '#5', '#6'],
                    datasets: [{
                        label: localize("Votes"),
                        backgroundColor: 'rgba(0, 255, 0, 0.7)',
                        data: props.data
                    }]
                },
                options: {
                    plugins: {
                        legend: {
                            display: false
                        }
                    },
                    scales: {
                        yAxes: {
                            min: 0,
                            ticks: {
                                precision: 0
                            }
                        }
                    }
                }
            });
        })
        
        return () => h("canvas", { ref: canvas, class: "chart" });
    }
});
