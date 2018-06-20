// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.data.Stat;

import javax.inject.Inject;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Stores instance suspension status and which hosts are allowed to go down in zookeeper.
 *
 * TODO: expiry of old application instances
 * @author tonytv
 */
public class ZookeeperStatusService implements StatusService {

    private static final Logger log = Logger.getLogger(ZookeeperStatusService.class.getName());

    final static String HOST_STATUS_BASE_PATH = "/vespa/host-status-service";
    final static String APPLICATION_STATUS_BASE_PATH = "/vespa/application-status-service";

    private final Curator curator;

    @Inject
    public ZookeeperStatusService(@Component Curator curator) {
        this.curator = curator;
    }

    @Override
    public ReadOnlyStatusRegistry forApplicationInstance(ApplicationInstanceReference applicationInstanceReference) {
        return new ReadOnlyStatusRegistry() {
            @Override
            public HostStatus getHostStatus(HostName hostName) {
                return getInternalHostStatus(applicationInstanceReference, hostName);
            }

            @Override
            public ApplicationInstanceStatus getApplicationInstanceStatus() {
                return getInternalApplicationInstanceStatus(applicationInstanceReference);
            }
        };
    }

    /**
     *  1) locks the status service for an application instance.
     *  2) fails all operations in this thread when the session is lost,
     *     since session loss might cause the lock to be lost.
     *     Since it only fails operations in this thread,
     *     all operations depending on a lock, including the locking itself, must be done in this thread.
     *     Note that since it is the thread that fails, all status operations in this thread will fail
     *     even if they're not supposed to be guarded by this lock
     *     (i.e. the request is for another applicationInstanceReference)
     */
    @Override
    public MutableStatusRegistry lockApplicationInstance_forCurrentThreadOnly(ApplicationInstanceReference applicationInstanceReference) {
        return lockApplicationInstance_forCurrentThreadOnly(applicationInstanceReference, 10);
    }

    @Override
    public Set<ApplicationInstanceReference> getAllSuspendedApplications() {
        try {
            Set<ApplicationInstanceReference> resultSet = new HashSet<>();

            // Return empty set if the base path does not exist
            Stat stat = curator.framework().checkExists().forPath(APPLICATION_STATUS_BASE_PATH);
            if (stat == null) return resultSet;

            // The path exist and we may have children
            for (String appRefStr : curator.framework().getChildren().forPath(APPLICATION_STATUS_BASE_PATH)) {
                ApplicationInstanceReference appRef = OrchestratorUtil.parseAppInstanceReference(appRefStr);
                resultSet.add(appRef);
            }

            return resultSet;
        } catch (Exception e) {
            log.log(LogLevel.DEBUG, "Something went wrong while listing out applications in suspend.", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public MutableStatusRegistry lockApplicationInstance_forCurrentThreadOnly(
            ApplicationInstanceReference applicationInstanceReference,
            long timeoutSeconds) {
        String lockPath = applicationInstanceLock2Path(applicationInstanceReference);
        Lock lock = new Lock(lockPath, curator);
        lock.acquire(Duration.ofSeconds(timeoutSeconds));

        try {
            return new ZkMutableStatusRegistry(lock, applicationInstanceReference);
        } catch (Throwable t) {
            // In case the constructor throws an exception.
            lock.close();
            throw t;
        }
    }

    private InterProcessSemaphoreMutex acquireMutexOrThrow(long timeout, TimeUnit timeoutTimeUnit, String lockPath) throws Exception {
        InterProcessSemaphoreMutex mutex = new InterProcessSemaphoreMutex(curator.framework(), lockPath);

        log.log(LogLevel.DEBUG, "Waiting for lock on " + lockPath);
        boolean acquired = mutex.acquire(timeout, timeoutTimeUnit);
        if (!acquired) {
            log.log(LogLevel.DEBUG, "Timed out waiting for lock on " + lockPath);
            throw new TimeoutException("Timed out waiting for lock on " + lockPath);
        }
        log.log(LogLevel.DEBUG, "Successfully acquired lock on " + lockPath);
        return mutex;
    }

    private void setHostStatus(ApplicationInstanceReference applicationInstanceReference,
                               HostName hostName,
                               HostStatus status) {
        String path = hostAllowedDownPath(applicationInstanceReference, hostName);

        try {
            switch (status) {
                case NO_REMARKS:
                    deleteNode_ignoreNoNodeException(path,"Host already has state NO_REMARKS, path = " + path);
                    break;
                case ALLOWED_TO_BE_DOWN:
                    createNode_ignoreNodeExistsException(path,
                                                         "Host already has state ALLOWED_TO_BE_DOWN, path = " + path);
            }
        } catch (Exception e) {
            //TODO: IOException with explanation
            throw new RuntimeException(e);
        }
    }

    private void deleteNode_ignoreNoNodeException(String path, String debugLogMessageIfNotExists) throws Exception {
        try {
            curator.framework().delete().forPath(path);
        } catch (NoNodeException e) {
            log.log(LogLevel.DEBUG, debugLogMessageIfNotExists, e);
        }
    }

    private void createNode_ignoreNodeExistsException(String path, String debugLogMessageIfExists) throws Exception {
        try {
            curator.framework().create()
                    .creatingParentsIfNeeded()
                    .forPath(path);
        } catch (NodeExistsException e) {
            log.log(LogLevel.DEBUG, debugLogMessageIfExists, e);
        }
    }

    //TODO: Eliminate repeated calls to getHostStatus, replace with bulk operation.
    private HostStatus getInternalHostStatus(ApplicationInstanceReference applicationInstanceReference, HostName hostName) {
        try {
            Stat statOrNull = curator.framework().checkExists().forPath(
                    hostAllowedDownPath(applicationInstanceReference, hostName));

            return (statOrNull == null) ? HostStatus.NO_REMARKS : HostStatus.ALLOWED_TO_BE_DOWN;
        } catch (Exception e) {
            //TODO: IOException with explanation - Should we only catch IOExceptions or are they a special case?
            throw new RuntimeException(e);
        }
    }

    /** Common implementation for the two internal classes that sets ApplicationInstanceStatus. */
    private ApplicationInstanceStatus getInternalApplicationInstanceStatus(ApplicationInstanceReference applicationInstanceReference) {
        try {
            Stat statOrNull = curator.framework().checkExists().forPath(
                    applicationInstanceSuspendedPath(applicationInstanceReference));

            return (statOrNull == null) ? ApplicationInstanceStatus.NO_REMARKS : ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HostStatus getHostStatusWithLock(
            final ApplicationInstanceReference applicationInstanceReference,
            final HostName hostName) {
        return getInternalHostStatus(applicationInstanceReference, hostName);
    }

    private static String applicationInstancePath(ApplicationInstanceReference applicationInstanceReference) {
        return HOST_STATUS_BASE_PATH + '/' +
                applicationInstanceReference.tenantId() + ":" + applicationInstanceReference.applicationInstanceId();
    }

    private static String hostsAllowedDownPath(ApplicationInstanceReference applicationInstanceReference) {
        return applicationInstancePath(applicationInstanceReference) + "/hosts-allowed-down";
    }

    private static String applicationInstanceLockPath(ApplicationInstanceReference applicationInstanceReference) {
        return applicationInstancePath(applicationInstanceReference) + "/lock";
    }

    private static String applicationInstanceLock2Path(ApplicationInstanceReference applicationInstanceReference) {
        return applicationInstancePath(applicationInstanceReference) + "/lock2";
    }

    private String applicationInstanceSuspendedPath(ApplicationInstanceReference applicationInstanceReference) {
        return APPLICATION_STATUS_BASE_PATH + "/" + OrchestratorUtil.toRestApiFormat(applicationInstanceReference);
    }

    private static String hostAllowedDownPath(ApplicationInstanceReference applicationInstanceReference, HostName hostname) {
        return hostsAllowedDownPath(applicationInstanceReference) + '/' + hostname.s();
    }

    private class ZkMutableStatusRegistry implements MutableStatusRegistry {
        private final Lock lock;
        private final ApplicationInstanceReference applicationInstanceReference;

        public ZkMutableStatusRegistry(
                Lock lock,
                ApplicationInstanceReference applicationInstanceReference) {
            this.lock = lock;
            this.applicationInstanceReference = applicationInstanceReference;
        }

        @Override
        public void setHostState(final HostName hostName, final HostStatus status) {
            setHostStatus(applicationInstanceReference, hostName, status);
        }

        @Override
        public void setApplicationInstanceStatus(ApplicationInstanceStatus applicationInstanceStatus) {
            String path = applicationInstanceSuspendedPath(applicationInstanceReference);

            try {
                switch (applicationInstanceStatus) {
                    case NO_REMARKS:
                        deleteNode_ignoreNoNodeException(path,
                                "Instance is already in state NO_REMARKS, path = " + path);
                        break;
                    case ALLOWED_TO_BE_DOWN:
                        createNode_ignoreNodeExistsException(path,
                                "Instance is already in state ALLOWED_TO_BE_DOWN, path = " + path);
                        break;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public HostStatus getHostStatus(final HostName hostName) {
            return getHostStatusWithLock(applicationInstanceReference, hostName);
        }

        @Override
        public ApplicationInstanceStatus getApplicationInstanceStatus() {
            return getInternalApplicationInstanceStatus(applicationInstanceReference);
        }

        @Override
        @NoThrow
        public void close()  {
            try {
                lock.close();
            } catch (RuntimeException e) {
                // We may want to avoid logging some exceptions that may be expected, like when session expires.
                log.log(LogLevel.WARNING, "Failed to close application lock for " +
                        ZookeeperStatusService.class.getSimpleName() + ", will ignore and continue", e);
            }
        }
    }
}
