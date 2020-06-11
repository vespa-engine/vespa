// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.transaction.AbstractTransaction;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.curator.Curator;

import java.io.File;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;

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
    private final SessionPreparer sessionPreparer;
    private final File serverDBSessionDir;
    private final SessionZooKeeperClient sessionZooKeeperClient;

    /**
     * Create a session. This involves loading the application, validating it and distributing it.
     *
     * @param sessionId The session id for this session.
     */
    public LocalSession(TenantName tenant, long sessionId, SessionPreparer sessionPreparer,
                        ApplicationPackage applicationPackage, SessionZooKeeperClient sessionZooKeeperClient,
                        File serverDBSessionDir, TenantApplications applicationRepo) {
        super(tenant, sessionId, sessionZooKeeperClient);
        this.serverDBSessionDir = serverDBSessionDir;
        this.applicationPackage = applicationPackage;
        this.sessionZooKeeperClient = sessionZooKeeperClient;
        this.applicationRepo = applicationRepo;
        this.sessionPreparer = sessionPreparer;
    }

    public ConfigChangeActions prepare(DeployLogger logger, 
                                       PrepareParams params, 
                                       Optional<ApplicationSet> currentActiveApplicationSet, 
                                       Path tenantPath,
                                       Instant now) {
        applicationRepo.createApplication(params.getApplicationId()); // TODO jvenstad: This is wrong, but it has to be done now, since preparation can change the application ID of a session :(
        logger.log(Level.FINE, "Created application " + params.getApplicationId());
        Curator.CompletionWaiter waiter = zooKeeperClient.createPrepareWaiter();
        ConfigChangeActions actions = sessionPreparer.prepare(applicationRepo.getHostValidator(), logger, params,
                                                              currentActiveApplicationSet, tenantPath, now,
                                                              serverDBSessionDir, applicationPackage, sessionZooKeeperClient);
        setPrepared();
        waiter.awaitCompletion(params.getTimeoutBudget().timeLeft());
        return actions;
    }

    public ApplicationFile getApplicationFile(Path relativePath, Mode mode) {
        if (mode.equals(Mode.WRITE)) {
            markSessionEdited();
        }
        return applicationPackage.getFile(relativePath);
    }

    private void setPrepared() {
        setStatus(Session.Status.PREPARE);
    }

    private Transaction createSetStatusTransaction(Status status) {
        return zooKeeperClient.createWriteStatusTransaction(status);
    }

    private void setStatus(Session.Status newStatus) {
        zooKeeperClient.writeStatus(newStatus);
    }

    public Transaction createActivateTransaction() {
        zooKeeperClient.createActiveWaiter();
        Transaction transaction = createSetStatusTransaction(Status.ACTIVATE);
        transaction.add(applicationRepo.createPutTransaction(zooKeeperClient.readApplicationId(), getSessionId()).operations());
        return transaction;
    }

    private void markSessionEdited() {
        setStatus(Session.Status.NEW);
    }

    public long getActiveSessionAtCreate() {
        return applicationPackage.getMetaData().getPreviousActiveGeneration();
    }

    /** Add transactions to delete this session to the given nested transaction */
    public void delete(NestedTransaction transaction) {
        transaction.add(zooKeeperClient.deleteTransaction(), FileTransaction.class);
        transaction.add(FileTransaction.from(FileOperations.delete(serverDBSessionDir.getAbsolutePath())));
    }

    public void waitUntilActivated(TimeoutBudget timeoutBudget) {
        zooKeeperClient.getActiveWaiter().awaitCompletion(timeoutBudget.timeLeft());
    }

    public enum Mode {
        READ, WRITE
    }

    public ApplicationMetaData getMetaData() {
        return applicationPackage.getMetaData();
    }

    // The rest of this class should be moved elsewhere ...
    
    private static class FileTransaction extends AbstractTransaction {
        
        public static FileTransaction from(FileOperation operation) {
            FileTransaction transaction = new FileTransaction();
            transaction.add(operation);
            return transaction;
        }

        @Override
        public void prepare() { }

        @Override
        public void commit() {
            for (Operation operation : operations())
                ((FileOperation)operation).commit();
        }

    }
    
    /** Factory for file operations */
    private static class FileOperations {
        
        /** Creates an operation which recursively deletes the given path */
        public static DeleteOperation delete(String pathToDelete) {
            return new DeleteOperation(pathToDelete);
        }
        
    }
    
    private interface FileOperation extends Transaction.Operation {

        void commit();
        
    }

    /** 
     * Recursively deletes this path and everything below. 
     * Succeeds with no action if the path does not exist.
     */
    private static class DeleteOperation implements FileOperation {

        private final String pathToDelete;
        
        DeleteOperation(String pathToDelete) {
            this.pathToDelete = pathToDelete;
        }
        
        @Override
        public void commit() {
            // TODO: Check delete access in prepare()
            IOUtils.recursiveDeleteDir(new File(pathToDelete));
        }

    }

}
