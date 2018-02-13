// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.transaction.Transaction;

import java.util.List;

/**
 * The applications of a tenant
 *
 * @author Ulf Lilleengen
 */
public interface TenantApplications {

    /**
     * List the active applications of a tenant in this config server.
     *
     * @return a list of {@link com.yahoo.config.provision.ApplicationId}s that are active.
     */
    List<ApplicationId> listApplications();

    /**
     * Register active application and adds it to the repo. If it already exists it is overwritten.
     *
     * @param applicationId An {@link com.yahoo.config.provision.ApplicationId} that represents an active application.
     * @param sessionId Id of the session containing the application package for this id.
     */
    Transaction createPutApplicationTransaction(ApplicationId applicationId, long sessionId);

    /**
     * Return the stored session id for a given application.
     *
     * @param  applicationId an {@link ApplicationId}
     * @return session id of given application id.
     * @throws IllegalArgumentException if the application does not exist
     */
    long getSessionIdForApplication(ApplicationId applicationId);

    /**
     * Returns a transaction which deletes this application
     *
     * @param applicationId an {@link ApplicationId} to delete.
     */
    Transaction deleteApplication(ApplicationId applicationId);

    /**
     * Removes unused applications
     *
     */
    void removeUnusedApplications();

    /**
     * Closes the application repo. Once a repo has been closed, it should not be used again.
     */
    void close();

}
