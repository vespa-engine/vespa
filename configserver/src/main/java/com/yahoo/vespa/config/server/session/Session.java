// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.tenant.TenantRepository;

/**
 * A session represents an instance of an application that can be edited, prepared and activated. This
 * class represents the common stuff between sessions working on the local file
 * system ({@link LocalSession}s) and sessions working on zookeeper {@link RemoteSession}s.
 *
 * @author Ulf Lilleengen
 */
public abstract class Session {

    private final long sessionId;
    protected final TenantName tenant;
    protected final SessionZooKeeperClient zooKeeperClient;

    protected Session(TenantName tenant, long sessionId, SessionZooKeeperClient zooKeeperClient) {
        this.tenant = tenant;
        this.sessionId = sessionId;
        this.zooKeeperClient = zooKeeperClient;
    }
    /**
     * Retrieve the session id for this session.
     * @return the session id.
     */
    public final long getSessionId() {
        return sessionId;
    }

    public Session.Status getStatus() {
        return zooKeeperClient.readStatus();
    }

    @Override
    public String toString() {
        return "Session,id=" + sessionId;
    }

    /**
     * Represents the status of this session.
     */
    public enum Status {
        NEW, PREPARE, ACTIVATE, DEACTIVATE, NONE;

        public static Status parse(String data) {
            for (Status status : Status.values()) {
                if (status.name().equals(data)) {
                    return status;
                }
            }
            return Status.NEW;
        }
    }
    
    public TenantName getTenant() {
        return tenant;
    }

    /**
     * Helper to provide a log message preamble for code dealing with sessions
     * @return log preamble
     */
    public String logPre() {
        return TenantRepository.logPre(getTenant());
    }

}
