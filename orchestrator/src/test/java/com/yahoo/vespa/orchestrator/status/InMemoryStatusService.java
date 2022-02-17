// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.OrchestratorContext;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * In-memory implementation of StatusService.
 *
 * @author hakon
 */
public class InMemoryStatusService implements StatusService {

    private final ConcurrentHashMap<ApplicationInstanceReference, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ApplicationInstanceReference, Map<HostName, HostInfo>> hostInfos = new ConcurrentHashMap<>();

    @Override
    public ApplicationLock lockApplication(OrchestratorContext context, ApplicationInstanceReference reference) throws UncheckedTimeoutException {
        ReentrantLock lock = locks.computeIfAbsent(reference, (ignored) -> new ReentrantLock());

        try {
            if (!lock.tryLock(context.getTimeLeft().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new UncheckedTimeoutException("Timed out trying to acquire the lock on " + reference);
            }
        } catch (InterruptedException e) {
            throw new UncheckedTimeoutException("Interrupted", e);
        }

        return new ApplicationLock() {
            @Override
            public ApplicationInstanceReference getApplicationInstanceReference() {
                return reference;
            }

            @Override
            public HostInfos getHostInfos() {
                return getHostInfosByApplicationResolver().apply(reference);
            }

            @Override
            public void setHostState(HostName hostName, HostStatus status) {
                if (status == HostStatus.NO_REMARKS) {
                    applicationHostInfo(reference).remove(hostName);
                } else {
                    applicationHostInfo(reference).put(hostName, HostInfo.createSuspended(status, Instant.EPOCH));
                }
            }

            @Override
            public ApplicationInstanceStatus getApplicationInstanceStatus() {
                return ApplicationInstanceStatus.NO_REMARKS;
            }

            @Override
            public void setApplicationInstanceStatus(ApplicationInstanceStatus applicationInstanceStatus) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
                lock.unlock();
            }
        };
    }

    private Map<HostName, HostInfo> applicationHostInfo(ApplicationInstanceReference reference) {
        return hostInfos.getOrDefault(reference, Map.of());
    }

    @Override
    public Set<ApplicationInstanceReference> getAllSuspendedApplications() {
        return Set.of();
    }

    @Override
    public Function<ApplicationInstanceReference, HostInfos> getHostInfosByApplicationResolver() {
        return reference -> new HostInfos(Map.copyOf(applicationHostInfo(reference)));
    }

    @Override
    public ApplicationInstanceStatus getApplicationInstanceStatus(ApplicationInstanceReference application) {
        return ApplicationInstanceStatus.NO_REMARKS;
    }

    @Override
    public HostInfo getHostInfo(ApplicationInstanceReference reference, HostName hostName) {
        return getHostInfosByApplicationResolver().apply(reference).getOrNoRemarks(hostName);
    }

    @Override
    public void onApplicationActivate(ApplicationInstanceReference reference, Set<HostName> hostnames) {
        Map<HostName, HostInfo> currentHostInfos = hostInfos.computeIfAbsent(reference, (ignored) -> new HashMap<>());
        hostnames.forEach(currentHostInfos::remove);
    }

    @Override
    public void onApplicationRemove(ApplicationInstanceReference reference) {
        hostInfos.remove(reference);
    }
}
