// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.TenantName;
import com.yahoo.transaction.Transaction;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;

import java.util.Optional;

/**
 * Overrides application package fetching, because this part is hard to do without feeding a full app.
 *
 * @author Ulf Lilleengen
 */
public class MockSessionZKClient extends SessionZooKeeperClient {

    private ApplicationPackage app = null;
    private Optional<AllocatedHosts> info = Optional.empty();
    private Session.Status sessionStatus;

    public MockSessionZKClient(Curator curator, TenantName tenantName, long sessionId) {
        this(curator, tenantName, sessionId, (ApplicationPackage)null);
    }

    public MockSessionZKClient(Curator curator, TenantName tenantName, long sessionId, Optional<AllocatedHosts> allocatedHosts) {
        this(curator, tenantName, sessionId);
        this.info = allocatedHosts;
    }

    public MockSessionZKClient(Curator curator, TenantName tenantName, long sessionId, ApplicationPackage application) {
        super(curator, TenantRepository.getSessionsPath(tenantName).append(String.valueOf(sessionId)));
        this.app = application;
    }

    public MockSessionZKClient(ApplicationPackage app) {
        super(new MockCurator(), Path.createRoot());
        this.app = app;
    }

    @Override
    public ApplicationPackage loadApplicationPackage() {
        if (app != null) return app;
        return new MockApplicationPackage.Builder().withEmptyServices().build();
    }

    @Override
    AllocatedHosts getAllocatedHosts() {
        return info.orElseThrow(() -> new IllegalStateException("Could not find allocated hosts"));
    }

    @Override
    public Transaction createWriteStatusTransaction(Session.Status status) {
        return new DummyTransaction().add((DummyTransaction.RunnableOperation) () -> {
            sessionStatus = status;
        });
    }

    @Override
    public Session.Status readStatus() {
        return sessionStatus;
    }
}
