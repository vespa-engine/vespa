// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.transaction.AbstractTransaction;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.*;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;

import java.io.File;
import java.time.Instant;
import java.util.Optional;

/**
 * A LocalSession is a session that has been created locally on this configserver. A local session can be edited and
 * prepared. Deleting a local session will ensure that the local filesystem state and global zookeeper state is
 * cleaned for this session.
 *
 * @author Ulf Lilleengen
 */
// This is really the store of an application, whether it is active or in an edit session
// TODO: Separate the "application store" and "session" aspects - the latter belongs in the HTTP layer   -bratseth
public class LocalSession extends Session implements Comparable<LocalSession> {

    private final ApplicationPackage applicationPackage;
    private final TenantApplications applicationRepo;
    private final SessionPreparer sessionPreparer;
    private final SessionContext sessionContext;
    private final File serverDB;
    private final SuperModelGenerationCounter superModelGenerationCounter;

    /**
     * Create a session. This involves loading the application, validating it and distributing it.
     *
     * @param sessionId The session id for this session.
     */
    // TODO tenant in SessionContext?
    public LocalSession(TenantName tenant, long sessionId, SessionPreparer sessionPreparer, SessionContext sessionContext) {
        super(tenant, sessionId, sessionContext.getSessionZooKeeperClient());
        this.serverDB = sessionContext.getServerDBSessionDir();
        this.applicationPackage = sessionContext.getApplicationPackage();
        this.applicationRepo = sessionContext.getApplicationRepo();
        this.sessionPreparer = sessionPreparer;
        this.sessionContext = sessionContext;
        this.superModelGenerationCounter = sessionContext.getSuperModelGenerationCounter();
    }

    public ConfigChangeActions prepare(DeployLogger logger, 
                                       PrepareParams params, 
                                       Optional<ApplicationSet> currentActiveApplicationSet, 
                                       Path tenantPath,
                                       Instant now) {
        Curator.CompletionWaiter waiter = zooKeeperClient.createPrepareWaiter();
        ConfigChangeActions actions = sessionPreparer.prepare(sessionContext, logger, params,
                                                              currentActiveApplicationSet, tenantPath, now);
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

    private Transaction setActive() {
        Transaction transaction = createSetStatusTransaction(Status.ACTIVATE);
        transaction.add(applicationRepo.createPutApplicationTransaction(zooKeeperClient.readApplicationId(), getSessionId()).operations());
        return transaction;
    }

    private Transaction createSetStatusTransaction(Status status) {
        return zooKeeperClient.createWriteStatusTransaction(status);
    }

    private void setStatus(Session.Status newStatus) {
        zooKeeperClient.writeStatus(newStatus);
    }

    public Transaction createActivateTransaction() {
        zooKeeperClient.createActiveWaiter();
        superModelGenerationCounter.increment();
        return setActive();
    }

    public Transaction createDeactivateTransaction() {
        return createSetStatusTransaction(Status.DEACTIVATE);
    }

    private void markSessionEdited() {
        setStatus(Session.Status.NEW);
    }

    public long getActiveSessionAtCreate() {
        return applicationPackage.getMetaData().getPreviousActiveGeneration();
    }

    // Note: Assumes monotonically increasing session ids
    public boolean isNewerThan(long sessionId) {
        return getSessionId() > sessionId;
    }

    /** Delete this session */
    // TODO: Use transactional delete instead
    public void delete() {
        superModelGenerationCounter.increment();
        IOUtils.recursiveDeleteDir(serverDB);
        zooKeeperClient.delete();
    }

    /** Add transactions to delete this session to the given nested transaction */
    public void delete(NestedTransaction transaction) {
        transaction.add(zooKeeperClient.deleteTransaction(), FileTransaction.class);
        transaction.add(FileTransaction.from(FileOperations.delete(serverDB.getAbsolutePath())), SuperModelGenerationCounter.IncrementTransaction.class);
        transaction.add(superModelGenerationCounter.incrementTransaction());
    }

    @Override
    public int compareTo(LocalSession rhs) {
        Long lhsId = getSessionId();
        Long rhsId = rhs.getSessionId();
        return lhsId.compareTo(rhsId);
    }

    // in seconds
    public long getCreateTime() {
        return zooKeeperClient.readCreateTime();
    }

    public void waitUntilActivated(TimeoutBudget timeoutBudget) {
        zooKeeperClient.getActiveWaiter().awaitCompletion(timeoutBudget.timeLeft());
    }

    public void setApplicationId(ApplicationId applicationId) {
        zooKeeperClient.writeApplicationId(applicationId);
    }

    public void setVespaVersion(Version version) {
        zooKeeperClient.writeVespaVersion(version);
    }

    public enum Mode {
        READ, WRITE
    }

    public ApplicationMetaData getMetaData() {
        return applicationPackage.getMetaData();
    }

    public ApplicationId getApplicationId() { return zooKeeperClient.readApplicationId(); }

    public Version getVespaVersion() { return zooKeeperClient.readVespaVersion(); }

    public AllocatedHosts getAllocatedHosts() {
        return zooKeeperClient.getAllocatedHosts();
    }

    @Override
    public String logPre() {
        if (getApplicationId().equals(ApplicationId.defaultId())) {
            return TenantRepository.logPre(getTenant());
        } else {
            return TenantRepository.logPre(getApplicationId());
        }
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
        
        public DeleteOperation(String pathToDelete) {
            this.pathToDelete = pathToDelete;
        }
        
        @Override
        public void commit() {
            // TODO: Check delete access in prepare()
            IOUtils.recursiveDeleteDir(new File(pathToDelete));
        }

    }

}
