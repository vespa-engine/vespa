// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.orchestrator.status;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.Curator;
import org.apache.zookeeper.KeeperException;

import java.util.logging.Logger;

class ZkMutableStatusService implements MutableStatusService {

    private static final Logger log = Logger.getLogger(ZkMutableStatusService.class.getName());

    private final ZkStatusService statusService;
    private final Curator curator;
    private final Runnable onClose;
    private final ApplicationInstanceReference reference;
    private final boolean probe;
    private final HostInfosCache hostInfosCache;

    ZkMutableStatusService(ZkStatusService statusService,
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
    public ApplicationInstanceStatus getStatus() {
        return statusService.getApplicationInstanceStatus(reference);
    }

    @Override
    public HostInfos getHostInfos() {
        return hostInfosCache.getHostInfos(reference);
    }

    @Override
    public void setHostState(final HostName hostName, final HostStatus status) {
        if (probe) return;
        log.log(LogLevel.INFO, "Setting host " + hostName + " to status " + status);
        hostInfosCache.setHostStatus(reference, hostName, status);
    }

    @Override
    public void setApplicationInstanceStatus(ApplicationInstanceStatus applicationInstanceStatus) {
        if (probe) return;

        log.log(LogLevel.INFO, "Setting app " + reference.asString() + " to status " + applicationInstanceStatus);

        String path = statusService.applicationInstanceSuspendedPath(reference);
        switch (applicationInstanceStatus) {
            case NO_REMARKS:
                deleteNode_ignoreNoNodeException(path);
                break;
            case ALLOWED_TO_BE_DOWN:
                createNode_ignoreNodeExistsException(path);
                break;
        }
    }

    @Override
    public void close()  {
        try {
            onClose.run();
        } catch (RuntimeException e) {
            // We may want to avoid logging some exceptions that may be expected, like when session expires.
            log.log(LogLevel.WARNING,
                    "Failed close application lock in " +
                    ZkMutableStatusService.class.getSimpleName() + ", will ignore and continue",
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
