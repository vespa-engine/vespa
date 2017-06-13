// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import org.apache.curator.SessionFailRetryLoop;
import org.apache.curator.SessionFailRetryLoop.Mode;
import org.apache.curator.SessionFailRetryLoop.SessionFailedException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.data.Stat;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    //For debug purposes only: Used to check that operations depending on a lock is done from a single thread,
    //and that a threads doing operations actually owns a corresponding lock,
    //and that a single thread only owns a single lock (across all ZookeeperStatusServices)
    @GuardedBy("threadsHoldingLock")
    private static final Map<Thread, ApplicationInstanceReference> threadsHoldingLock = new HashMap<>();

    final static String HOST_STATUS_BASE_PATH = "/vespa/host-status-service";
    final static String APPLICATION_STATUS_BASE_PATH = "/vespa/application-status-service";

    private final CuratorFramework curatorFramework;

    @Inject
    public ZookeeperStatusService(@Component Curator curator) {
        this(curator.framework());
    }

    /**
     * Called via public constructor on directly on testing.
     */
    ZookeeperStatusService(CuratorFramework curatorFramework) {
        this.curatorFramework = curatorFramework;
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
        return lockApplicationInstance_forCurrentThreadOnly(applicationInstanceReference, 10, TimeUnit.SECONDS);
    }

    @Override
    public Set<ApplicationInstanceReference> getAllSuspendedApplications() {
        try {
            Set<ApplicationInstanceReference> resultSet = new HashSet<>();

            // Return empty set if the base path does not exist
            Stat stat = curatorFramework.checkExists().forPath(APPLICATION_STATUS_BASE_PATH);
            if (stat == null) return resultSet;

            // The path exist and we may have children
            for (String appRefStr : curatorFramework.getChildren().forPath(APPLICATION_STATUS_BASE_PATH)) {
                ApplicationInstanceReference appRef = OrchestratorUtil.parseAppInstanceReference(appRefStr);
                resultSet.add(appRef);
            }

            return resultSet;
        } catch (Exception e) {
            log.log(LogLevel.DEBUG, "Something went wrong while listing out applications in suspend.", e);
            throw new RuntimeException(e);
        }
    }

    MutableStatusRegistry lockApplicationInstance_forCurrentThreadOnly(ApplicationInstanceReference applicationInstanceReference,
                                                                       long timeout,
                                                                       TimeUnit timeoutTimeUnit) {
        Thread currentThread = Thread.currentThread();

        //Due to limitations in SessionFailRetryLoop.
        assertThreadDoesNotHoldLock(currentThread,"Can't lock " + applicationInstanceReference);

        try {
            SessionFailRetryLoop sessionFailRetryLoop = 
                    curatorFramework.getZookeeperClient().newSessionFailRetryLoop(Mode.FAIL);
            sessionFailRetryLoop.start();
            try {
                String lockPath = applicationInstanceLockPath(applicationInstanceReference);
                InterProcessSemaphoreMutex mutex = acquireMutexOrThrow(timeout, timeoutTimeUnit, lockPath);

                synchronized (threadsHoldingLock) {
                    threadsHoldingLock.put(currentThread, applicationInstanceReference);
                }

                return new ZkMutableStatusRegistry(mutex, sessionFailRetryLoop, applicationInstanceReference, currentThread);
            } catch (Throwable t) {
                sessionFailRetryLoop.close();
                throw t;
            }
        } catch (Exception e) {
            //TODO: IOException with explanation
            throw new RuntimeException(e);
        }
    }

    private void assertThreadDoesNotHoldLock(Thread currentThread, String message) {
        synchronized (threadsHoldingLock) {
            if (threadsHoldingLock.containsKey(currentThread)) {
                throw new AssertionError(message + ", already have a lock on " + threadsHoldingLock.get(currentThread));
            }
        }
    }

    private InterProcessSemaphoreMutex acquireMutexOrThrow(long timeout, TimeUnit timeoutTimeUnit, String lockPath) throws Exception {
        InterProcessSemaphoreMutex mutex = new InterProcessSemaphoreMutex(curatorFramework, lockPath);

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
        assertThreadHoldsLock(applicationInstanceReference);

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

    private static void assertThreadHoldsLock(ApplicationInstanceReference applicationInstanceReference) {
        synchronized (threadsHoldingLock) {
            ApplicationInstanceReference lockedApplicationInstanceReference =
                    threadsHoldingLock.get(Thread.currentThread());

            if (lockedApplicationInstanceReference == null) {
                throw new AssertionError("The current thread does not own any status service locks. " +
                        "Application Instance = " + applicationInstanceReference);
            }

            if (!lockedApplicationInstanceReference.equals(applicationInstanceReference)) {
                throw new AssertionError("The current thread does not have a lock on " +
                        "application instance " + applicationInstanceReference +
                        ", but instead have a lock on " + lockedApplicationInstanceReference);
            }
        }
    }

    private void deleteNode_ignoreNoNodeException(String path, String debugLogMessageIfNotExists) throws Exception {
        try {
            curatorFramework.delete().forPath(path);
        } catch (NoNodeException e) {
            log.log(LogLevel.DEBUG, debugLogMessageIfNotExists, e);
        }
    }

    private void createNode_ignoreNodeExistsException(String path, String debugLogMessageIfExists) throws Exception {
        try {
            curatorFramework.create()
                    .creatingParentsIfNeeded()
                    .forPath(path);
        } catch (NodeExistsException e) {
            log.log(LogLevel.DEBUG, debugLogMessageIfExists, e);
        }
    }

    //TODO: Eliminate repeated calls to getHostStatus, replace with bulk operation.
    private HostStatus getInternalHostStatus(ApplicationInstanceReference applicationInstanceReference, HostName hostName) {
        try {
            Stat statOrNull = curatorFramework.checkExists().forPath(
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
            Stat statOrNull = curatorFramework.checkExists().forPath(
                    applicationInstanceSuspendedPath(applicationInstanceReference));

            return (statOrNull == null) ? ApplicationInstanceStatus.NO_REMARKS : ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HostStatus getHostStatusWithLock(
            final ApplicationInstanceReference applicationInstanceReference,
            final HostName hostName) {
        assertThreadHoldsLock(applicationInstanceReference);
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

    private String applicationInstanceSuspendedPath(ApplicationInstanceReference applicationInstanceReference) {
        return APPLICATION_STATUS_BASE_PATH + "/" + OrchestratorUtil.toRestApiFormat(applicationInstanceReference);
    }

    private static String hostAllowedDownPath(ApplicationInstanceReference applicationInstanceReference, HostName hostname) {
        return hostsAllowedDownPath(applicationInstanceReference) + '/' + hostname.s();
    }

    private class ZkMutableStatusRegistry implements MutableStatusRegistry {
        private final InterProcessSemaphoreMutex mutex;
        private final SessionFailRetryLoop sessionFailRetryLoop;
        private final ApplicationInstanceReference applicationInstanceReference;
        private final Thread lockingThread;

        public ZkMutableStatusRegistry(
                InterProcessSemaphoreMutex mutex,
                SessionFailRetryLoop sessionFailRetryLoop,
                ApplicationInstanceReference applicationInstanceReference,
                Thread lockingThread) {

            this.mutex = mutex;
            this.sessionFailRetryLoop = sessionFailRetryLoop;
            this.applicationInstanceReference = applicationInstanceReference;
            this.lockingThread = lockingThread;
        }

        @Override
        public void setHostState(final HostName hostName, final HostStatus status) {
            setHostStatus(applicationInstanceReference, hostName, status);
        }

        @Override
        public void setApplicationInstanceStatus(ApplicationInstanceStatus applicationInstanceStatus) {
            assertThreadHoldsLock(applicationInstanceReference);

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
            synchronized (threadsHoldingLock) {
                threadsHoldingLock.remove(lockingThread, applicationInstanceReference);
            }

            try {
                mutex.release();
            } catch (Exception e) {
                if (e.getCause() instanceof SessionFailedException) {
                    log.log(LogLevel.DEBUG, "Session expired, mutex should be freed automatically", e);
                } else {
                    //Failing to unlock the mutex should not fail the request,
                    //since the status database has already been updated at this point.
                    log.log(LogLevel.WARNING, "Failed unlocking application instance " + applicationInstanceReference, e);
                }
            }

            //Similar precondition checked in sessionFailRetryLoop.close,
            //but this has more useful debug output.
            if (lockingThread != Thread.currentThread()) {
                throw new AssertionError("LockHandle should only be used from a single thread. "
                        + "Application instance = " + applicationInstanceReference
                        + " Locking thread = " + lockingThread
                        + " Current thread = " + Thread.currentThread());
            }

            try {
                sessionFailRetryLoop.close();
            } catch (Exception e) {
                log.log(LogLevel.ERROR, "Failed closing SessionRetryLoop", e);
            }
        }
    }
}
