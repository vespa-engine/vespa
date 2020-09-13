// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A session cache that can store any type of {@link Session}.
 *
 * @author Ulf Lilleengen
 * @author hmusum
 */
public class SessionCache<SESSIONTYPE extends Session> {

    private final HashMap<Long, SESSIONTYPE> sessions = new HashMap<>();

    public synchronized void putSession(SESSIONTYPE session) {
        sessions.put(session.getSessionId(), session);
    }

    synchronized void removeSession(long id) {
        sessions.remove(id);
    }

    public synchronized SESSIONTYPE getSession(long id) {
        return sessions.get(id);
    }

    public synchronized List<SESSIONTYPE> getSessions() {
        return new ArrayList<>(sessions.values());
    }
    
}
