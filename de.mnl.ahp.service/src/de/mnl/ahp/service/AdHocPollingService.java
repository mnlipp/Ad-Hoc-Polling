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

package de.mnl.ahp.service;

import de.mnl.ahp.service.events.CreatePoll;
import de.mnl.ahp.service.events.GetPoll;
import de.mnl.ahp.service.events.ListPolls;
import de.mnl.ahp.service.events.PollData;
import de.mnl.ahp.service.events.PollExpired;
import de.mnl.ahp.service.events.PollState;
import de.mnl.ahp.service.events.Vote;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.Components.Timer;
import org.jgrapes.core.annotation.Handler;
import org.osgi.framework.BundleContext;

/**
 *
 */
public class AdHocPollingService extends Component {

    private Map<Integer, InternalPollData> data = new HashMap<>();

    public AdHocPollingService(Channel componentChannel,
            BundleContext context) {
        super(componentChannel);
    }

    @Handler
    public void onCreatePoll(CreatePoll event) {
        while (true) {
            int digit1 = (int) (Math.random() * 9 + 1);
            int digit2 = (int) (Math.random() * 9 + 1);
            int number = digit1 * 1000 + digit2 * 100 + digit1 * 10 + digit2;
            synchronized (data) {
                if (!data.containsKey(number)) {
                    InternalPollData pd
                        = new InternalPollData(event.adminId(), number);
                    data.put(number, pd);
                    fire(new PollState(pd.toPollData()));
                    break;
                }
            }
        }
    }

    @Handler
    public void onListPolls(ListPolls event) {
        synchronized (data) {
            for (Map.Entry<Integer, InternalPollData> entry : data.entrySet()) {
                if (!event.adminId().equals(entry.getValue().adminId())) {
                    continue;
                }
                fire(
                    new PollState(entry.getValue().toPollData()));
            }
        }
    }

    @Handler
    public void onCheckPoll(GetPoll event) {
        event.setResult(Optional.ofNullable(data.get(event.pollId()))
            .map(ipd -> ipd.toPollData()).orElse(null));
    }

    @Handler
    public void onVote(Vote event) {
        synchronized (data) {
            Optional.ofNullable(data.get(event.pollId())).ifPresent(
                pollData -> {
                    pollData.incrementCounter(event.index());
                    fire(new PollState(pollData.toPollData()));
                });
        }
    }

    private class InternalPollData {
        private String adminId;
        private int pollId;
        private Instant startedAt;
        private int[] counter = new int[6];
        Timer expiaryTimer;

        public InternalPollData(String adminId, int pollId) {
            this.adminId = adminId;
            this.pollId = pollId;
            startedAt = Instant.now();
            for (int i = 0; i < 6; i++) {
                counter[i] = 0;
            }
            expiaryTimer = Components.schedule(timer -> {
                synchronized (data) {
                    data.remove(pollId);
                    newEventPipeline().fire(new PollExpired(adminId, pollId),
                        channel());
                }
            }, Duration.ofMinutes(30));
        }

        public String adminId() {
            return adminId;
        }

        public int pollId() {
            return pollId;
        }

        public Instant startedAt() {
            return startedAt;
        }

        public InternalPollData incrementCounter(int index) {
            counter[index] += 1;
            return this;
        }

        PollData toPollData() {
            return new PollData(adminId, pollId(), startedAt(),
                expiaryTimer.scheduledFor(),
                Arrays.copyOf(counter, counter.length));
        }
    }
}
