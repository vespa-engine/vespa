// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.application.api.ApplicationPackage;
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
    private final Optional<LocalSession> localSession;

    /**
     * Creates a session. This involves loading the application, validating it and distributing it.
     *
     * @param tenant The name of the tenant creating session
     * @param sessionId The session id for this session.
     * @param zooKeeperClient a SessionZooKeeperClient instance
     */
    RemoteSession(TenantName tenant, long sessionId, SessionZooKeeperClient zooKeeperClient, Optional<LocalSession> localSession) {
        this(tenant, sessionId, zooKeeperClient, Optional.empty(), localSession);
    }

    /**
     * Creates a remote session, with application set
     *
     * @param tenant The name of the tenant creating session
     * @param sessionId The session id for this session.
     * @param zooKeeperClient a SessionZooKeeperClient instance
     * @param applicationSet current application set for this session
     */
    public RemoteSession(TenantName tenant,
                         long sessionId,
                         SessionZooKeeperClient zooKeeperClient,
                         Optional<ApplicationSet> applicationSet,
                         Optional<LocalSession> localSession) {
        super(tenant, sessionId, zooKeeperClient);
        this.applicationSet = applicationSet;
        this.localSession = localSession;
    }

    /**
     * Creates a remote session from a local session
     *
     * @param localSession a LocalSession
     */
    RemoteSession(LocalSession localSession) {
        // Need to set application package
        super(localSession.getTenantName(),
              localSession.getSessionId(),
              localSession.getSessionZooKeeperClient(),
              localSession.getApplicationPackage());
        this.applicationSet = Optional.empty();
        this.localSession = Optional.of(localSession);
    }

    @Override
    public Optional<ApplicationSet> applicationSet() { return applicationSet; }

    public synchronized RemoteSession activated(ApplicationSet applicationSet) {
        Objects.requireNonNull(applicationSet, "applicationSet cannot be null");
        return new RemoteSession(tenant, sessionId, sessionZooKeeperClient, Optional.of(applicationSet), localSession);
    }

    public synchronized RemoteSession deactivated() {
        return new RemoteSession(tenant, sessionId, sessionZooKeeperClient, Optional.empty(), localSession);
    }

    public Optional<LocalSession> localSession() { return localSession; }

    @Override
    public String toString() {
        return super.toString() + ",application set=" + applicationSet;
    }

    @Override
    public ApplicationPackage getApplicationPackage() {
        return localSession.isPresent() ? localSession.get().getApplicationPackage() : super.getApplicationPackage();
    }

}
