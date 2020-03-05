// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.recipes.CuratorCounter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author hakonhall
 */
public class HostInfosCache implements HostInfosService {
    final static String HOST_STATUS_CACHE_COUNTER_PATH = "/vespa/host-status-service-cache-counter";

    private final CuratorCounter counter;
    private final HostInfosService wrappedService;

    private final AtomicLong cacheGeneration;
    private final Map<ApplicationInstanceReference, HostInfos> suspendedHosts = new ConcurrentHashMap<>();

    HostInfosCache(Curator curator, HostInfosService wrappedService) {
        this.counter = new CuratorCounter(curator, HOST_STATUS_CACHE_COUNTER_PATH);
        this.wrappedService = wrappedService;
        this.cacheGeneration = new AtomicLong(counter.get());
    }

    public void refreshCache() {
        long newCacheGeneration = counter.get();
        if (cacheGeneration.getAndSet(newCacheGeneration) != newCacheGeneration) {
            suspendedHosts.clear();
        }
    }

    public HostInfos getCachedHostInfos(ApplicationInstanceReference reference) {
        return suspendedHosts.computeIfAbsent(reference, wrappedService::getHostInfos);
    }

    @Override
    public HostInfos getHostInfos(ApplicationInstanceReference reference) {
        refreshCache();
        return getCachedHostInfos(reference);
    }

    @Override
    public boolean setHostStatus(ApplicationInstanceReference reference, HostName hostName, HostStatus hostStatus) {
        boolean isException = true;
        boolean modified = false;
        try {
            modified = wrappedService.setHostStatus(reference, hostName, hostStatus);
            isException = false;
        } finally {
            if (modified || isException) {
                // ensure the next get, on any config server, will repopulate the cache from zk.
                counter.next();
            }
        }

        return modified;
    }

    @Override
    public void removeApplication(ApplicationInstanceReference reference) {
        wrappedService.removeApplication(reference);
        suspendedHosts.remove(reference);
    }

    @Override
    public void removeHosts(ApplicationInstanceReference reference, Set<HostName> hostnames) {
        if (hostnames.size() > 0) {
            wrappedService.removeHosts(reference, hostnames);
            counter.next();
        }
    }
}
