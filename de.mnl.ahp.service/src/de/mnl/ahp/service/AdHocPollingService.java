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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.osgi.framework.BundleContext;

/**
 *
 */
public class AdHocPollingService extends Component {

	private Map<Integer, PollData> data = new HashMap<>();
	
	public AdHocPollingService(Channel componentChannel, BundleContext context) {
		super(componentChannel);
	}

	@Handler
	public void onCreatePoll(CreatePoll event) {
		while (true) {
			int digit1 = (int)(Math.random() * 9 + 1);
			int digit2 = (int)(Math.random() * 9 + 1);
			int number = digit1 * 1000 + digit2 * 100 + digit1 * 10 + digit2;
			synchronized(data) {
				if (!data.containsKey(number)) {
					data.put(number, new PollData());
					break;
				}
			}
		}
	}
	
	public class PollData {
		private Instant startedAt;
		private int[] counter = new int[6];
		
		public PollData() {
			startedAt = Instant.now();
			for (int i = 0; i < 6; i++) {
				counter[i] = 0;
			}
		}

		public PollData incrementCounter(int index) {
			counter[index] += 1;
			return this;
		}
		
	}
}
