// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Version;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.yahoo.vespa.config.server.NotFoundException;

/**
 * Used during config request handling to route to the right config model
 * based on application id and version.
 * 
 * @author Vegard Sjonfjell
 */
public final class ApplicationMapper {

    private final Map<ApplicationId, ApplicationSet> requestHandlers = new ConcurrentHashMap<>();

    private ApplicationSet getApplicationSet(ApplicationId applicationId) {
        ApplicationSet list = requestHandlers.get(applicationId);
        if (list != null) {
            return list;
        }

        throw new NotFoundException("No such application id: " + applicationId);
    }

    /**
     * Register a Application to an application id and specific vespa version
     */
    public void register(ApplicationId applicationId, ApplicationSet applicationSet) {
        requestHandlers.put(applicationId, applicationSet);
    }

    /**
     * Remove all applications associated with this application id
     */
    public void remove(ApplicationId applicationId) {
        requestHandlers.remove(applicationId);
    }

    /**
     * Retrieve the Application corresponding to this application id and specific vespa version.
     *
     * @return the matching application, or null if none matches
     */
    public Application getForVersion(ApplicationId applicationId, Optional<Version> vespaVersion, Instant now) throws VersionDoesNotExistException {
        return getApplicationSet(applicationId).getForVersionOrLatest(vespaVersion, now);
    }

    /** Returns whether this registry has an application for the given application id */
    public boolean hasApplication(ApplicationId applicationId, Instant now) {
        return hasApplicationForVersion(applicationId, Optional.<Version>empty(), now);
    }

    /** Returns whether this registry has an application for the given application id and vespa version */
    public boolean hasApplicationForVersion(ApplicationId applicationId, Optional<Version> vespaVersion, Instant now) {
        try {
            return getForVersion(applicationId, vespaVersion, now) != null;
        }
        catch (VersionDoesNotExistException | NotFoundException ex) {
            return false;
        }
    }

    /**
     * Get the number of applications registered
     */
    public int numApplications() {
        return requestHandlers.size();
    }

}
