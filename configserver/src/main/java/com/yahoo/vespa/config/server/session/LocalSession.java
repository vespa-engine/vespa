// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.path.Path;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.TenantApplications;

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

    protected final ApplicationPackage applicationPackage;
    private final TenantApplications applicationRepo;

    /**
     * Creates a session. This involves loading the application, validating it and distributing it.
     *
     * @param sessionId The session id for this session.
     */
    public LocalSession(TenantName tenant, long sessionId, ApplicationPackage applicationPackage,
                        SessionZooKeeperClient sessionZooKeeperClient,
                        TenantApplications applicationRepo) {
        super(tenant, sessionId, sessionZooKeeperClient);
        this.applicationPackage = applicationPackage;
        this.applicationRepo = applicationRepo;
    }

    public ApplicationFile getApplicationFile(Path relativePath, Mode mode) {
        if (mode.equals(Mode.WRITE)) {
            markSessionEdited();
        }
        return applicationPackage.getFile(relativePath);
    }

    void setPrepared() {
        setStatus(Session.Status.PREPARE);
    }

    private Transaction createSetStatusTransaction(Status status) {
        return sessionZooKeeperClient.createWriteStatusTransaction(status);
    }

    private void setStatus(Session.Status newStatus) {
        sessionZooKeeperClient.writeStatus(newStatus);
    }

    public Transaction createActivateTransaction() {
        sessionZooKeeperClient.createActiveWaiter();
        Transaction transaction = createSetStatusTransaction(Status.ACTIVATE);
        transaction.add(applicationRepo.createPutTransaction(sessionZooKeeperClient.readApplicationId(), getSessionId()).operations());
        return transaction;
    }

    private void markSessionEdited() {
        setStatus(Session.Status.NEW);
    }

    public long getActiveSessionAtCreate() {
        return applicationPackage.getMetaData().getPreviousActiveGeneration();
    }

    public void waitUntilActivated(TimeoutBudget timeoutBudget) {
        sessionZooKeeperClient.getActiveWaiter().awaitCompletion(timeoutBudget.timeLeft());
    }

    public enum Mode {
        READ, WRITE
    }

    public ApplicationMetaData getMetaData() { return applicationPackage.getMetaData(); }

    public ApplicationPackage getApplicationPackage() { return applicationPackage; }

}
