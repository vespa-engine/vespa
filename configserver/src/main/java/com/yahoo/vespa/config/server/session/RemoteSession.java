// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.application.ApplicationSet;

import java.util.Objects;
import java.util.Optional;

/**
 * A RemoteSession represents a session created on another config server. This session can
 * be regarded as read only, and this interface only allows reading information about a session.
 *
 * @author Ulf Lilleengen
 */
public class RemoteSession extends Session {

    private final Optional<ApplicationSet> applicationSet;

    /**
     * Creates a session. This involves loading the application, validating it and distributing it.
     *
     * @param tenant The name of the tenant creating session
     * @param sessionId The session id for this session.
     * @param zooKeeperClient a SessionZooKeeperClient instance
     */
    RemoteSession(TenantName tenant, long sessionId, SessionZooKeeperClient zooKeeperClient) {
        this(tenant, sessionId, zooKeeperClient, Optional.empty());
    }

    /**
     * Creates a remote session, with application set
     *
     * @param tenant The name of the tenant creating session
     * @param sessionId The session id for this session.
     * @param zooKeeperClient a SessionZooKeeperClient instance
     * @param applicationSet current application set for this session
     */
    RemoteSession(TenantName tenant, long sessionId, SessionZooKeeperClient zooKeeperClient, Optional<ApplicationSet> applicationSet) {
        super(tenant, sessionId, zooKeeperClient);
        this.applicationSet = applicationSet;
    }

    @Override
    public Optional<ApplicationSet> applicationSet() { return applicationSet; }

    public synchronized RemoteSession activated(ApplicationSet applicationSet) {
        Objects.requireNonNull(applicationSet, "applicationSet cannot be null");
        return new RemoteSession(tenant, sessionId, sessionZooKeeperClient, Optional.of(applicationSet));
    }

    public synchronized RemoteSession deactivated() {
        return new RemoteSession(tenant, sessionId, sessionZooKeeperClient, Optional.empty());
    }

    @Override
    public String toString() {
        return super.toString() + ",application set=" + applicationSet;
    }

}
