// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of the StatusService interface for testing.
 *
 * @author oyving
 */
public class InMemoryStatusService implements StatusService {

    private final Map<HostName, HostStatus> hostServiceStatus = new HashMap<>();
    private final Set<ApplicationInstanceReference> applicationStatus = new HashSet<>();
    private final LockService<ApplicationInstanceReference> instanceLockService = new LockService<>();

    private void setHostStatus(HostName hostName, HostStatus status) {
        hostServiceStatus.put(hostName, status);
    }

    @Override
    public ReadOnlyStatusRegistry forApplicationInstance(ApplicationInstanceReference applicationInstanceReference) {
        return new ReadOnlyStatusRegistry() {
            @Override
            public HostStatus getHostStatus(HostName hostName) {
                return hostServiceStatus.getOrDefault(hostName, HostStatus.NO_REMARKS);
            }

            @Override
            public ApplicationInstanceStatus getApplicationInstanceStatus() {
                return applicationStatus.contains(applicationInstanceReference) ?
                        ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN : ApplicationInstanceStatus.NO_REMARKS;
            }
        };
    }

    @Override
    public MutableStatusRegistry lockApplicationInstance_forCurrentThreadOnly(ApplicationInstanceReference applicationInstanceReference) {
        return lockApplicationInstance_forCurrentThreadOnly(applicationInstanceReference, 10);
    }

    @Override
    public MutableStatusRegistry lockApplicationInstance_forCurrentThreadOnly(
            ApplicationInstanceReference applicationInstanceReference,
            long timeoutSeconds) {
        Lock lock = instanceLockService.get(applicationInstanceReference);
        return new InMemoryMutableStatusRegistry(lock, applicationInstanceReference);
    }

    @Override
    public Set<ApplicationInstanceReference> getAllSuspendedApplications() {
        return applicationStatus;
    }

    private class InMemoryMutableStatusRegistry implements MutableStatusRegistry {

        private final Lock lockHandle;
        private final ApplicationInstanceReference ref;

        public InMemoryMutableStatusRegistry(Lock lockHandle, ApplicationInstanceReference ref) {
            this.lockHandle = lockHandle;
            this.ref = ref;
        }

        @Override
        public void setHostState(HostName hostName, HostStatus status) {
            setHostStatus(hostName, status);
        }

        @Override
        public void setApplicationInstanceStatus(ApplicationInstanceStatus applicationInstanceStatus) {
            if (applicationInstanceStatus == ApplicationInstanceStatus.NO_REMARKS) {
                applicationStatus.remove(ref);
            } else {
                applicationStatus.add(ref);
            }
        }

        @Override
        public HostStatus getHostStatus(HostName hostName) {
            return hostServiceStatus.getOrDefault(hostName, HostStatus.NO_REMARKS);
        }

        @Override
        public ApplicationInstanceStatus getApplicationInstanceStatus() {
            return applicationStatus.contains(ref) ? ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN :
            ApplicationInstanceStatus.NO_REMARKS;
        }

        @Override
        public void close() {
            //lockHandle.unlock();   TODO this casues illeal state monitor exception - how to use it properly
        }
    }

    private static class LockService<T> {

        private final Map<T, Lock> locks;

        public LockService() {
            this.locks = new HashMap<>();
        }

        public Lock get(T lockId) {
            synchronized (this) {
                Lock lock = locks.computeIfAbsent(
                    lockId,
                    id -> new ReentrantLock()
                );

                return lock;
            }
        }
    }

}
