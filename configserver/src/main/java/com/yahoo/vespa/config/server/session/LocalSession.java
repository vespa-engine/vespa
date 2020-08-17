// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.application.TenantApplications;

import static com.yahoo.vespa.curator.Curator.CompletionWaiter;

/**
 * A LocalSession is a session that has been created locally on this configserver. A local session can be edited and
 * prepared. Deleting a local session will ensure that the local filesystem state and global zookeeper state is
 * cleaned for this session.
 *
 * @author Ulf Lilleengen
 */
// This is really the store of an application, whether it is active or in an edit session
// TODO: Separate the "application store" and "session" aspects - the latter belongs in the HTTP layer   -bratseth
public class LocalSession extends Session {

    private final TenantApplications applicationRepo;

    /**
     * Creates a session. This involves loading the application, validating it and distributing it.
     *
     * @param sessionId The session id for this session.
     */
    public LocalSession(TenantName tenant, long sessionId, ApplicationPackage applicationPackage,
                        SessionZooKeeperClient sessionZooKeeperClient, TenantApplications applicationRepo) {
        super(tenant, sessionId, sessionZooKeeperClient, applicationPackage);
        this.applicationRepo = applicationRepo;
    }

    void setPrepared() {
        setStatus(Session.Status.PREPARE);
    }

    private Transaction createSetStatusTransaction(Status status) {
        return sessionZooKeeperClient.createWriteStatusTransaction(status);
    }

    public CompletionWaiter createActiveWaiter() {
        return sessionZooKeeperClient.createActiveWaiter();
    }

    public Transaction createActivateTransaction() {
        Transaction transaction = createSetStatusTransaction(Status.ACTIVATE);
        transaction.add(applicationRepo.createPutTransaction(sessionZooKeeperClient.readApplicationId(), getSessionId()).operations());
        return transaction;
    }

    public long getActiveSessionAtCreate() {
        return getMetaData().getPreviousActiveGeneration();
    }

    public enum Mode {
        READ, WRITE
    }

}
