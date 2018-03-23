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

package de.mnl.ahp.service.events;

import java.time.Instant;

/**
 *
 */
public class PollData {
    private String adminId;
	private int pollId;
	private Instant startedAt;
    private Instant expiresAt;
	private int[] counter;
	
    public PollData(String adminId, int pollId, Instant startedAt,
            Instant expiresAt, int[] counter) {
        this.adminId = adminId;
		this.pollId = pollId;
		this.startedAt = startedAt;
        this.expiresAt = expiresAt;
		this.counter = counter;
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
	
    public Instant expiresAt() {
        return expiresAt;
    }

	public int[] counter() {
		return counter;
	}
}
