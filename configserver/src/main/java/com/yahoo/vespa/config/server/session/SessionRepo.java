// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.transaction.AbstractTransaction;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.NotFoundException;

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
// TODO: This is a ZK cache. We should probably remove it, or make that explicit
public class SessionRepo<SESSIONTYPE extends Session> {

    private final HashMap<Long, SESSIONTYPE> sessions = new HashMap<>();

    public synchronized void addSession(SESSIONTYPE session) {
        internalAddSession(session);
    }

    /** Why is this needed? Because of implementation inheritance - see RemoteSessionRepo */
    protected synchronized final void internalAddSession(SESSIONTYPE session) {
        if (sessions.containsKey(session.getSessionId()))
            throw new IllegalArgumentException("There already exists a session with id '" + session.getSessionId() + "'");
        sessions.put(session.getSessionId(), session);
    }

    public synchronized void removeSessionOrThrow(long id) {
        internalRemoveSessionOrThrow(id);
    }

    /** Why is this needed? Because of implementation inheritance - see RemoteSessionRepo */
    protected synchronized final void internalRemoveSessionOrThrow(long id) {
        if ( ! sessions.containsKey(id))
            throw new IllegalArgumentException("No such session exists '" + id + "'");
        sessions.remove(id);
    }

    /** 
     * Removes a session in a transaction
     * 
     * @param id the id of the session to remove
     * @return the removed session, or null if none was found
     */
    public synchronized SESSIONTYPE removeSession(long id) { return sessions.remove(id); }
    
    public void removeSession(long id, NestedTransaction nestedTransaction) {
        SessionRepoTransaction transaction = new SessionRepoTransaction();
        transaction.addRemoveOperation(id);
        nestedTransaction.add(transaction);
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
    
    public class SessionRepoTransaction extends AbstractTransaction {

        public void addRemoveOperation(long sessionIdToRemove) {
            add(new RemoveOperation(sessionIdToRemove));
        }
        
        @Override
        public void prepare() { }

        @Override
        @SuppressWarnings("unchecked")
        public void commit() {
            for (Operation operation : operations())
                ((SessionOperation)operation).commit();
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public void rollbackOrLog() {
            for (Operation operation : operations())
                ((SessionOperation)operation).rollback();
        }
        
        public abstract class SessionOperation implements Transaction.Operation {
            
            abstract void commit();
            
            abstract void rollback();
            
        }
        
        public class RemoveOperation extends SessionOperation {
            
            private final long sessionIdToRemove;
            private SESSIONTYPE removed = null;
            
            public RemoveOperation(long sessionIdToRemove) {
                this.sessionIdToRemove = sessionIdToRemove;
            }

            @Override
            public void commit() {
                removed = removeSession(sessionIdToRemove);
            }

            @Override
            public void rollback() {
                if (removed != null)
                    addSession(removed);
            }

        }

    }
    
}
