// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.http.NotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * A generic session repository that can store any type of session that extends the abstract interface.
 *
 * @author lulf
 * @since 5.1
 */
public class SessionRepo<SESSIONTYPE extends Session> {

    private final HashMap<Long, SESSIONTYPE> sessions = new HashMap<>();

    public synchronized void addSession(SESSIONTYPE session) {
        final long sessionId = session.getSessionId();
        if (sessions.containsKey(sessionId)) {
            throw new IllegalArgumentException("There already exists a session with id '" + sessionId + "'");
        }
        sessions.put(sessionId, session);
    }

    public synchronized void removeSession(long id) {
        if ( ! sessions.containsKey(id)) {
            throw new IllegalArgumentException("No such session exists '" + id + "'");
        }
        sessions.remove(id);
    }

    /**
     * Gets a Session
     *
     * @param id session id
     * @return a session belonging to the id supplied, or null if no session with the id was found
     */
    public synchronized SESSIONTYPE getSession(long id) {
        return sessions.get(id);
    }

    /**
     * Gets a Session with a timeout
     *
     * @param id              session id
     * @param timeoutInMillis timeout for getting session (loops and wait for session to show up if not found)
     * @return a session belonging to the id supplied, or null if no session with the id was found
     */
    public synchronized SESSIONTYPE getSession(long id, long timeoutInMillis) {
        try {
            return internalGetSession(id, timeoutInMillis);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while retrieving session with id " + id);
        }
    }

    private synchronized SESSIONTYPE internalGetSession(long id, long timeoutInMillis) throws InterruptedException {
        TimeoutBudget timeoutBudget = new TimeoutBudget(Clock.systemUTC(), Duration.ofMillis(timeoutInMillis));
        do {
            SESSIONTYPE session = getSession(id);
            if (session != null) {
                return session;
            }
            wait(100);
        } while (timeoutBudget.hasTimeLeft());
        throw new NotFoundException("Unable to retrieve session with id " + id + " before timeout was reached");
    }

    public synchronized Collection<SESSIONTYPE> listSessions() {
        return new ArrayList<>(sessions.values());
    }
}
