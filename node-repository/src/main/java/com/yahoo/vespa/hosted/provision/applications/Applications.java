// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ProvisionLock;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.util.List;
import java.util.Optional;

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
        // read and write all to make sure they are stored in the latest version of the serialized format
        for (ApplicationId id : ids()) {
            try (Mutex lock = db.lock(id)) {
                get(id).ifPresent(application -> put(application, lock));
            }
        }
    }

    /** Returns the ids of all applications */
    public List<ApplicationId> ids() { return db.readApplicationIds(); }

    /** Returns the application with the given id, or empty if it does not exist */
    public Optional<Application> get(ApplicationId id) {
        return db.readApplication(id);
    }

    // TODO: Require ProvisionLock instead of Mutex
    public void put(Application application, Mutex applicationLock) {
        NestedTransaction transaction = new NestedTransaction();
        put(application, transaction, applicationLock);
        transaction.commit();
    }

    // TODO: Require ProvisionLock instead of Mutex
    public void put(Application application, NestedTransaction transaction, Mutex applicationLock) {
        db.writeApplication(application, transaction);
    }

    public void remove(ApplicationId application, NestedTransaction transaction, @SuppressWarnings("unused") ProvisionLock lock) {
        db.deleteApplication(application, transaction);
    }

}
