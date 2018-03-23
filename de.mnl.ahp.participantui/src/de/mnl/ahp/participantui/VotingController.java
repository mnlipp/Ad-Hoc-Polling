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

package de.mnl.ahp.participantui;

import de.mnl.ahp.service.events.PollData;

import java.io.Serializable;
import java.time.Instant;

/**
 * 
 */
public class VotingController implements Serializable {
	private static final long serialVersionUID = -4844487793046604718L;

    public enum State {
        Start, Duplicate, Unknown, Authorized, Voted
    }
	
	private State state = State.Start;
    private int pollId;
    private Instant pollStartedAt;
    private Instant pollExpiresAt;

    public State state() {
        return state;
    }

    public void reset() {
        state = State.Start;
    }

    public void unknownPoll() {
        state = State.Unknown;
    }

    public boolean isUnknown() {
        return state == State.Unknown;
    }

    public void duplicateVote() {
        state = State.Duplicate;
    }

    public boolean isDuplicate() {
        return state == State.Duplicate;
    }

    public void authorized(PollData pollData) {
        pollId = pollData.pollId();
        pollStartedAt = pollData.startedAt();
        pollExpiresAt = pollData.expiresAt();
        state = State.Authorized;
    }

    public int pollId() {
        return pollId;
    }

    public Instant pollStartedAt() {
        return pollStartedAt;
    }

    public Instant pollExpiresAt() {
        return pollExpiresAt;
    }

    public void voted() {
        state = State.Voted;
    }

}
