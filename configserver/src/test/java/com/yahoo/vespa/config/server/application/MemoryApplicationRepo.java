// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.session.DummyTransaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In memory {@link ApplicationRepo} to be used when testing.
 *
 * @author lulf
 * @since 5.1
 */
public class MemoryApplicationRepo implements ApplicationRepo {
    private final Map<ApplicationId, Long> applications = new LinkedHashMap<>();
    private boolean isOpen = true;

    @Override
    public List<ApplicationId> listApplications() {
        List<ApplicationId> lst = new ArrayList<>();
        lst.addAll(applications.keySet());
        return lst;
    }

    @Override
    public Transaction createPutApplicationTransaction(ApplicationId applicationId, long sessionId) {
        return new DummyTransaction().add((DummyTransaction.RunnableOperation) () -> {
            applications.put(applicationId, sessionId);
        });
    }

    @Override
    public long getSessionIdForApplication(ApplicationId id) {
        if (applications.containsKey(id)) {
            return applications.get(id);
        }
        return 0;
    }

    @Override
    public void deleteApplication(ApplicationId id) {
        applications.remove(id);
    }

    @Override
    public void close() {
        isOpen = false;
    }

    public boolean isOpen() {
        return isOpen;
    }
}
