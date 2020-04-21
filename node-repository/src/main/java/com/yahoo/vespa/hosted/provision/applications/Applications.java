// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An (in-memory, for now) repository of the node repo's view of applications.
 *
 * This is multithread safe.
 *
 * @author bratseth
 */
public class Applications {

    private final CuratorDatabaseClient db;

    public Applications(CuratorDatabaseClient db) {
        this.db = db;
    }

    /** Returns the application with the given id, or null if it does not exist and should not be created */
    public Application get(ApplicationId id, boolean create) {
        return db.readApplication(id).orElse(create ? new Application(id) : null);
    }

    public void set(Application application, Mutex applicationLock) {
        db.writeApplication(application);
    }

}
