// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.transaction.Mutex;

import java.util.concurrent.ConcurrentHashMap;

/**
 * An (in-memory, for now) repository of the node repo's view of applications.
 *
 * This is multithread safe.
 *
 * @author bratseth
 */
public class Applications {

    private final ConcurrentHashMap<ApplicationId, Application> applications = new ConcurrentHashMap<>();

    /** Returns the application with the given id, or null if it does not exist and should not be created */
    public Application get(ApplicationId applicationId, boolean create) {
        return applications.computeIfAbsent(applicationId, id -> create ? new Application() : null);
    }

    public void set(ApplicationId id, Application application, Mutex applicationLock) {
        applications.put(id, application);
    }

}
