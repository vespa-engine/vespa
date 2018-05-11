// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.TimeoutBudget;

import java.io.File;

/**
 * A session factory responsible for creating deploy sessions.
 *
 * @author lulf
 */
public interface SessionFactory {

    /**
     * Creates a new deployment session from an application package.
     *
     *
     *
     * @param applicationDirectory a File pointing to an application.
     * @param applicationId application id for this new session.
     * @param timeoutBudget Timeout for creating session and waiting for other servers.
     * @return a new session
     */
    LocalSession createSession(File applicationDirectory, ApplicationId applicationId, TimeoutBudget timeoutBudget);

    /**
     * Creates a new deployment session from an already existing session.
     *
     * @param existingSession The session to use as base
     * @param logger a deploy logger where the deploy log will be written.
     * @param timeoutBudget Timeout for creating session and waiting for other servers.
     * @return a new session
     */
    LocalSession createSessionFromExisting(LocalSession existingSession, DeployLogger logger, TimeoutBudget timeoutBudget);

}
