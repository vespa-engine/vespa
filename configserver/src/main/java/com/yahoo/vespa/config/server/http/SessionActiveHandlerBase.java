// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.provision.Provisioner;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.vespa.config.server.ActivateLock;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.deploy.Deployer;
import com.yahoo.vespa.config.server.deploy.Deployment;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.LocalSessionRepo;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * @author lulf
 */
public class SessionActiveHandlerBase extends SessionHandler {

    public SessionActiveHandlerBase(Executor executor, AccessLog accessLog) {
        super(executor, accessLog);
    }

    protected void activate(HttpRequest request,
                            LocalSessionRepo localSessionRepo,
                            ActivateLock activateLock,
                            TimeoutBudget timeoutBudget,
                            Optional<Provisioner> hostProvisioner,
                            LocalSession localSession) {
        // TODO: Use an injected deployer from the callers of this instead
        // TODO: And then get rid of the activateLock and localSessionRepo arguments in deployFromPreparedSession
        Deployer deployer = new Deployer(null, HostProvisionerProvider.from(hostProvisioner), null, null);
        Deployment deployment = deployer.deployFromPreparedSession(localSession, activateLock, localSessionRepo, timeoutBudget.timeLeft());
        deployment.setIgnoreLockFailure(shouldIgnoreLockFailure(request));
        deployment.setIgnoreSessionStaleFailure(shouldIgnoreSessionStaleFailure(request));
        deployment.activate();
    }

    private boolean shouldIgnoreLockFailure(HttpRequest request) {
        return request.getBooleanProperty("force");
    }

    /**
     * True if this request should ignore activation failure because the session was made from an active session that is not active now
     * @param request a {@link com.yahoo.container.jdisc.HttpRequest}
     * @return true if ignore failure
     */
    private boolean shouldIgnoreSessionStaleFailure(HttpRequest request) {
        return request.getBooleanProperty("force");
    }

}
