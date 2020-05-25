// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A generic session repository that can store any type of session that extends the abstract interface.
 *
 * @author Ulf Lilleengen
 */
// TODO: This is a ZK cache. We should probably remove it, or make that explicit
public class SessionRepo<SESSIONTYPE extends Session> {

    private final HashMap<Long, SESSIONTYPE> sessions = new HashMap<>();

    public synchronized void addSession(SESSIONTYPE session) {
        if (sessions.containsKey(session.getSessionId()))
            throw new IllegalArgumentException("There already exists a session with id '" + session.getSessionId() + "'");
        sessions.put(session.getSessionId(), session);
    }

    synchronized void removeSession(long id) {
        if ( ! sessions.containsKey(id))
            throw new IllegalArgumentException("No session with id '" + id + "' exists");
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

    public synchronized List<SESSIONTYPE> listSessions() {
        return new ArrayList<>(sessions.values());
    }
    
}
