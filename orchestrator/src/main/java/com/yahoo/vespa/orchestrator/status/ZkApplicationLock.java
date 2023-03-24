// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.orchestrator.status;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.Curator;
import org.apache.zookeeper.KeeperException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ZooKeeper implementation of {@link ApplicationLock}.
 *
 * @author hakonhall
 */
class ZkApplicationLock implements ApplicationLock {

    private static final Logger log = Logger.getLogger(ZkApplicationLock.class.getName());

    private final ZkStatusService statusService;
    private final Curator curator;
    private final Runnable onClose;
    private final ApplicationInstanceReference reference;
    private final boolean probe;
    private final HostInfosCache hostInfosCache;

    ZkApplicationLock(ZkStatusService statusService,
                      Curator curator,
                      Runnable onClose,
                      ApplicationInstanceReference reference,
                      boolean probe,
                      HostInfosCache hostInfosCache) {
        this.statusService = statusService;
        this.curator = curator;
        this.onClose = onClose;
        this.reference = reference;
        this.probe = probe;
        this.hostInfosCache = hostInfosCache;
    }

    @Override
    public ApplicationInstanceReference getApplicationInstanceReference() {
        return reference;
    }

    @Override
    public ApplicationInstanceStatus getApplicationInstanceStatus() {
        return statusService.getApplicationInstanceStatus(reference);
    }

    @Override
    public HostInfos getHostInfos() {
        return hostInfosCache.getHostInfos(reference);
    }

    @Override
    public void setHostState(final HostName hostName, final HostStatus status) {
        if (probe) return;
        hostInfosCache.setHostStatus(reference, hostName, status);
    }

    @Override
    public void setApplicationInstanceStatus(ApplicationInstanceStatus applicationInstanceStatus) {
        if (probe) return;

        String path = statusService.applicationInstanceSuspendedPath(reference);
        switch (applicationInstanceStatus) {
            case NO_REMARKS -> deleteNode_ignoreNoNodeException(path);
            case ALLOWED_TO_BE_DOWN -> createNode_ignoreNodeExistsException(path);
        }
    }

    @Override
    public void close()  {
        try {
            onClose.run();
        } catch (RuntimeException e) {
            // We may want to avoid logging some exceptions that may be expected, like when session expires.
            log.log(Level.WARNING,
                    "Failed close application lock in " +
                    ZkApplicationLock.class.getSimpleName() + ", will ignore and continue",
                    e);
        }
    }

    void deleteNode_ignoreNoNodeException(String path) {
        try {
            curator.framework().delete().forPath(path);
        } catch (KeeperException.NoNodeException e) {
            // ok
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void createNode_ignoreNodeExistsException(String path) {
        try {
            curator.framework().create().creatingParentsIfNeeded().forPath(path);
        } catch (KeeperException.NodeExistsException e) {
            // ok
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
