// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.ReloadHandler;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.transaction.CuratorOperations;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The applications of a tenant, backed by ZooKeeper.
 *
 * Each application is stored under /config/v2/tenants/&lt;tenant&gt;/applications/&lt;applications&gt;,
 * the root contains the currently active session, if any, and sub-paths /preparing contains the session id
 * of whatever session may be activated next, if any, and /lock is used for synchronizing writes to all these paths.
 *
 * @author Ulf Lilleengen
 * @author jonmv
 */
public class TenantApplications {

    private static final Logger log = Logger.getLogger(TenantApplications.class.getName());

    private final Curator curator;
    private final Path applicationsPath;
    // One thread pool for all instances of this class
    private static final ExecutorService pathChildrenExecutor =
            Executors.newCachedThreadPool(ThreadFactoryFactory.getDaemonThreadFactory(TenantApplications.class.getName()));
    private final Curator.DirectoryCache directoryCache;
    private final ReloadHandler reloadHandler;
    private final TenantName tenant;

    private TenantApplications(Curator curator, Path applicationsPath, ReloadHandler reloadHandler, TenantName tenant) {
        this.curator = curator;
        this.applicationsPath = applicationsPath;
        curator.create(applicationsPath);
        this.reloadHandler = reloadHandler;
        this.tenant = tenant;
        this.directoryCache = curator.createDirectoryCache(applicationsPath.getAbsolute(), false, false, pathChildrenExecutor);
        this.directoryCache.start();
        this.directoryCache.addListener(this::childEvent);
    }

    public static TenantApplications create(Curator curator, ReloadHandler reloadHandler, TenantName tenant) {
        return new TenantApplications(curator, TenantRepository.getApplicationsPath(tenant), reloadHandler, tenant);
    }

    /**
     * List the active applications of a tenant in this config server.
     *
     * @return a list of {@link ApplicationId}s that are active.
     */
    public List<ApplicationId> activeApplications() {
        return curator.getChildren(applicationsPath).stream()
                      .filter(this::isValid)
                      .sorted()
                      .map(ApplicationId::fromSerializedForm)
                      .filter(id -> activeSessionOf(id).isPresent())
                      .collect(Collectors.toUnmodifiableList());
    }

    private boolean isValid(String appNode) { // TODO jvenstad: Remove after it has run once everywhere.
        try {
            ApplicationId.fromSerializedForm(appNode);
            return true;
        } catch (IllegalArgumentException __) {
            log.log(LogLevel.INFO, TenantRepository.logPre(tenant) + "Unable to parse application id from '" +
                    appNode + "'; deleting it as it shouldn't be here.");
            try {
                curator.delete(applicationsPath.append(appNode));
            }
            catch (Exception e) {
                log.log(LogLevel.WARNING, TenantRepository.logPre(tenant) + "Failed to clean up stray node '" + appNode + "'!", e);
            }
            return false;
        }
    }

    /** Returns the id of the currently active session for the given application, if any. Throws on unknown applications. */
    public OptionalLong activeSessionOf(ApplicationId id) {
        String data = curator.getData(applicationPath(id)).map(Utf8::toString)
                             .orElseThrow(() -> new IllegalArgumentException("Unknown application '" + id + "'."));
        return data.isEmpty() ? OptionalLong.empty() : OptionalLong.of(Long.parseLong(data));
    }

    /**
     * Returns a transaction which writes the given session id as the currently active for the given application.
     *
     * @param applicationId An {@link ApplicationId} that represents an active application.
     * @param sessionId Id of the session containing the application package for this id.
     */
    public Transaction createPutTransaction(ApplicationId applicationId, long sessionId) {
        return new CuratorTransaction(curator).add(CuratorOperations.setData(applicationPath(applicationId).getAbsolute(), Utf8.toAsciiBytes(sessionId)));
    }

    /**
     * Creates a node for the given application, marking its existence.
     */
    public void createApplication(ApplicationId id) {
        curator.create(applicationPath(id));
    }

    /**
     * Return the active session id for a given application.
     *
     * @param  applicationId an {@link ApplicationId}
     * @return session id of given application id.
     * @throws IllegalArgumentException if the application does not exist
     */
    public long requireActiveSessionOf(ApplicationId applicationId) {
        return activeSessionOf(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application '" + applicationId + "' has no active session."));
    }

    /**
     * Returns a transaction which deletes this application.
     */
    public CuratorTransaction createDeleteTransaction(ApplicationId applicationId) {
        return CuratorTransaction.from(CuratorOperations.delete(applicationPath(applicationId).getAbsolute()), curator);
    }

    /**
     * Removes all applications not known to this from the config server state.
     */
    public void removeUnusedApplications() {
        reloadHandler.removeApplicationsExcept(Set.copyOf(activeApplications()));
    }

    /**
     * Closes the application repo. Once a repo has been closed, it should not be used again.
     */
    public void close() {
        directoryCache.close();
    }

    private void childEvent(CuratorFramework client, PathChildrenCacheEvent event) {
        switch (event.getType()) {
            case CHILD_ADDED:
                applicationAdded(ApplicationId.fromSerializedForm(Path.fromString(event.getData().getPath()).getName()));
                break;
            // Event CHILD_REMOVED will be triggered on all config servers if deleteApplication() above is called on one of them
            case CHILD_REMOVED:
                applicationRemoved(ApplicationId.fromSerializedForm(Path.fromString(event.getData().getPath()).getName()));
                break;
            case CHILD_UPDATED:
                // do nothing, application just got redeployed
                break;
            default:
                break;
        }
        // We may have lost events and may need to remove applications.
        // New applications are added when session is added, not here. See RemoteSessionRepo.
        removeUnusedApplications();
    }

    private void applicationRemoved(ApplicationId applicationId) {
        reloadHandler.removeApplication(applicationId);
        log.log(LogLevel.INFO, TenantRepository.logPre(applicationId) + "Application removed: " + applicationId);
    }

    private void applicationAdded(ApplicationId applicationId) {
        log.log(LogLevel.DEBUG, TenantRepository.logPre(applicationId) + "Application added: " + applicationId);
    }

    private Path applicationPath(ApplicationId id) {
        return applicationsPath.append(id.serializedForm());
    }

}
