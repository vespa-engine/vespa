// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import com.yahoo.vespa.orchestrator.status.json.WireHostInfo;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.data.Stat;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Stores instance suspension status and which hosts are allowed to go down in zookeeper.
 *
 * TODO: expiry of old application instances
 * @author Tony Vaagenes
 */
public class ZookeeperStatusService implements StatusService {

    private static final Logger log = Logger.getLogger(ZookeeperStatusService.class.getName());

    final static String HOST_STATUS_BASE_PATH = "/vespa/host-status-service";
    final static String APPLICATION_STATUS_BASE_PATH = "/vespa/application-status-service";

    private final Curator curator;
    private final HostInfosCache hostInfosCache;
    private final Metric metric;
    private final Timer timer;

    /**
     * A cache of metric contexts for each possible dimension map. In practice, there is one dimension map
     * for each application, so up to hundreds of elements.
     */
    private final ConcurrentHashMap<Map<String, String>, Metric.Context> cachedContexts = new ConcurrentHashMap<>();

    @Inject
    public ZookeeperStatusService(@Component Curator curator, @Component Metric metric, @Component Timer timer) {
        this.curator = curator;
        this.metric = metric;
        this.timer = timer;

        // Insert a cache above some ZooKeeper accesses
        this.hostInfosCache = new HostInfosCache(curator, new HostInfosService() {
            @Override
            public HostInfos getHostInfos(ApplicationInstanceReference application) {
                return ZookeeperStatusService.this.getHostInfosFromZk(application);
            }

            @Override
            public boolean setHostStatus(ApplicationInstanceReference application, HostName hostName, HostStatus hostStatus) {
                return ZookeeperStatusService.this.setHostStatusInZk(application, hostName, hostStatus);
            }
        });
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

    /**
     * Cache is checked for freshness when this mapping is created, and may be invalidated again later
     * by other users of the cache. Since this function is backed by the cache, any such invalidation
     * will be reflected in the returned mapping; all users of the cache collaborate in repopulating it.
     */
    @Override
    public Function<ApplicationInstanceReference, HostInfos> getHostInfosByApplicationResolver() {
        hostInfosCache.refreshCache();
        return hostInfosCache::getCachedHostInfos;
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
    public MutableStatusRegistry lockApplicationInstance_forCurrentThreadOnly(
            OrchestratorContext context,
            ApplicationInstanceReference applicationInstanceReference) throws UncheckedTimeoutException {
        ApplicationId applicationId = OrchestratorUtil.toApplicationId(applicationInstanceReference);
        String app = applicationId.application().value() + "." + applicationId.instance().value();
        Map<String, String> dimensions = Map.of(
                "tenantName", applicationId.tenant().value(),
                "applicationId", applicationId.toFullString(),
                "app", app);
        Metric.Context metricContext = cachedContexts.computeIfAbsent(dimensions, metric::createContext);

        Duration duration = context.getTimeLeft();
        String lockPath = applicationInstanceLock2Path(applicationInstanceReference);
        Lock lock = new Lock(lockPath, curator);

        Instant startTime = timer.currentTime();
        Instant acquireEndTime;
        boolean lockAcquired = false;
        try {
            lock.acquire(duration);
            lockAcquired = true;
        } finally {
            acquireEndTime = timer.currentTime();
            double seconds = durationInSeconds(startTime, acquireEndTime);
            metric.set("orchestrator.lock.acquire-latency", seconds, metricContext);
            metric.set("orchestrator.lock.acquired", lockAcquired ? 1 : 0, metricContext);

            metric.add("orchestrator.lock.acquire", 1, metricContext);
            String acquireResultMetricName = lockAcquired ? "orchestrator.lock.acquire-success" : "orchestrator.lock.acquire-timedout";
            metric.add(acquireResultMetricName, 1, metricContext);
        }

        Runnable updateLockHoldMetric = () -> {
            Instant lockReleasedTime = timer.currentTime();
            double seconds = durationInSeconds(acquireEndTime, lockReleasedTime);
            metric.set("orchestrator.lock.hold-latency", seconds, metricContext);
        };

        try {
            return new ZkMutableStatusRegistry(lock, applicationInstanceReference, context.isProbe(), updateLockHoldMetric);
        } catch (Throwable t) {
            // In case the constructor throws an exception.
            updateLockHoldMetric.run();
            lock.close();
            throw t;
        }
    }

    private double durationInSeconds(Instant startInstant, Instant endInstant) {
        return Duration.between(startInstant, endInstant).toMillis() / 1000.0;
    }

    /** Do not call this directly: should be called behind a cache. */
    private boolean setHostStatusInZk(ApplicationInstanceReference applicationInstanceReference,
                                      HostName hostName,
                                      HostStatus status) {
        String hostAllowedDownPath = hostAllowedDownPath(applicationInstanceReference, hostName);

        boolean modified = false;
        try {
            switch (status) {
                case NO_REMARKS:
                    // Deprecated: Remove once 7.170 has rolled out to infrastructure
                    modified = deleteNode_ignoreNoNodeException(hostAllowedDownPath, "Host already has state NO_REMARKS, path = " + hostAllowedDownPath);
                    break;
                default:
                    // ignore, e.g. ALLOWED_TO_BE_DOWN should NOT create a new deprecated znode.
            }

            modified |= setHostInfoInZk(applicationInstanceReference, hostName, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return modified;
    }

    /** Returns false if no changes were made. */
    private boolean setHostInfoInZk(ApplicationInstanceReference application, HostName hostname, HostStatus status)
            throws Exception {
        String path = hostPath(application, hostname);

        if (status == HostStatus.NO_REMARKS) {
            return deleteNode_ignoreNoNodeException(path, "Host already has state NO_REMARKS, path = " + path);
        }

        Optional<HostInfo> currentHostInfo = readBytesFromZk(path).map(WireHostInfo::deserialize);
        if (currentHostInfo.isEmpty()) {
            Instant suspendedSince = timer.currentTime();
            HostInfo hostInfo = HostInfo.createSuspended(status, suspendedSince);
            byte[] hostInfoBytes = WireHostInfo.serialize(hostInfo);
            curator.framework().create().creatingParentsIfNeeded().forPath(path, hostInfoBytes);
        } else if (currentHostInfo.get().status() == status) {
            return false;
        } else {
            Instant suspendedSince = currentHostInfo.get().suspendedSince().orElseGet(timer::currentTime);
            HostInfo hostInfo = HostInfo.createSuspended(status, suspendedSince);
            byte[] hostInfoBytes = WireHostInfo.serialize(hostInfo);
            curator.framework().setData().forPath(path, hostInfoBytes);
        }

        return true;
    }

    private boolean deleteNode_ignoreNoNodeException(String path, String debugLogMessageIfNotExists) throws Exception {
        try {
            curator.framework().delete().forPath(path);
            return true;
        } catch (NoNodeException e) {
            log.log(LogLevel.DEBUG, debugLogMessageIfNotExists, e);
            return false;
        }
    }

    private boolean createNode_ignoreNodeExistsException(String path, String debugLogMessageIfExists) throws Exception {
        try {
            curator.framework().create()
                    .creatingParentsIfNeeded()
                    .forPath(path);
            return true;
        } catch (NodeExistsException e) {
            log.log(LogLevel.DEBUG, debugLogMessageIfExists, e);
            return false;
        }
    }

    private Optional<byte[]> readBytesFromZk(String path) throws Exception {
        try {
            return Optional.of(curator.framework().getData().forPath(path));
        } catch (NoNodeException e) {
            return Optional.empty();
        }
    }

    @Override
    public HostInfo getHostInfo(ApplicationInstanceReference applicationInstanceReference, HostName hostName) {
        return hostInfosCache.getHostInfos(applicationInstanceReference).getOrNoRemarks(hostName);
    }

    /** Do not call this directly: should be called behind a cache. */
    private HostInfos getHostInfosFromZk(ApplicationInstanceReference application) {
        String hostsRootPath = hostsPath(application);
        if (uncheck(() -> curator.framework().checkExists().forPath(hostsRootPath)) == null) {
            return new HostInfos();
        } else {
            List<String> hostnames = uncheck(() -> curator.framework().getChildren().forPath(hostsRootPath));
            Map<HostName, HostInfo> hostInfos = hostnames.stream().collect(Collectors.toMap(
                    hostname -> new HostName(hostname),
                    hostname -> {
                        byte[] bytes = uncheck(() -> curator.framework().getData().forPath(hostsRootPath + "/" + hostname));
                        return WireHostInfo.deserialize(bytes);
                    }));
            return new HostInfos(hostInfos);
        }
    }

    private <T> T uncheck(SupplierThrowingException<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    interface SupplierThrowingException<T> {
        T get() throws Exception;
    }

    @Override
    public ApplicationInstanceStatus getApplicationInstanceStatus(ApplicationInstanceReference applicationInstanceReference) {
        try {
            Stat statOrNull = curator.framework().checkExists().forPath(
                    applicationInstanceSuspendedPath(applicationInstanceReference));

            return (statOrNull == null) ? ApplicationInstanceStatus.NO_REMARKS : ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String applicationInstanceReferencePath(ApplicationInstanceReference applicationInstanceReference) {
        return HOST_STATUS_BASE_PATH + '/' +
                applicationInstanceReference.tenantId() + ":" + applicationInstanceReference.applicationInstanceId();
    }

    private static String applicationPath(ApplicationInstanceReference applicationInstanceReference) {
        ApplicationId applicationId = OrchestratorUtil.toApplicationId(applicationInstanceReference);
        return "/vespa/host-status/" + applicationId.serializedForm();
    }

    private static String hostsPath(ApplicationInstanceReference applicationInstanceReference) {
        return applicationPath(applicationInstanceReference) + "/hosts";
    }

    private static String hostsAllowedDownPath(ApplicationInstanceReference applicationInstanceReference) {
        return applicationInstanceReferencePath(applicationInstanceReference) + "/hosts-allowed-down";
    }

    private static String applicationInstanceLock2Path(ApplicationInstanceReference applicationInstanceReference) {
        return applicationInstanceReferencePath(applicationInstanceReference) + "/lock2";
    }

    private String applicationInstanceSuspendedPath(ApplicationInstanceReference applicationInstanceReference) {
        return APPLICATION_STATUS_BASE_PATH + "/" + OrchestratorUtil.toRestApiFormat(applicationInstanceReference);
    }

    private static String hostAllowedDownPath(ApplicationInstanceReference applicationInstanceReference, HostName hostname) {
        return hostsAllowedDownPath(applicationInstanceReference) + '/' + hostname.s();
    }

    private static String hostPath(ApplicationInstanceReference application, HostName hostname) {
        return hostsPath(application) + "/" + hostname.s();
    }

    private class ZkMutableStatusRegistry implements MutableStatusRegistry {

        private final Lock lock;
        private final ApplicationInstanceReference applicationInstanceReference;
        private final boolean probe;
        private final Runnable onLockRelease;

        public ZkMutableStatusRegistry(Lock lock,
                                       ApplicationInstanceReference applicationInstanceReference,
                                       boolean probe,
                                       Runnable onLockRelease) {
            this.lock = lock;
            this.applicationInstanceReference = applicationInstanceReference;
            this.probe = probe;
            this.onLockRelease = onLockRelease;
        }

        @Override
        public ApplicationInstanceStatus getStatus() {
            return getApplicationInstanceStatus(applicationInstanceReference);
        }

        @Override
        public HostInfo getHostInfo(HostName hostName) {
            return ZookeeperStatusService.this.getHostInfo(applicationInstanceReference, hostName);
        }

        @Override
        public HostInfos getHostInfos() {
            return hostInfosCache.getHostInfos(applicationInstanceReference);
        }

        @Override
        public void setHostState(final HostName hostName, final HostStatus status) {
            if (probe) return;
            log.log(LogLevel.INFO, "Setting host " + hostName + " to status " + status);
            hostInfosCache.setHostStatus(applicationInstanceReference, hostName, status);
        }

        @Override
        public void setApplicationInstanceStatus(ApplicationInstanceStatus applicationInstanceStatus) {
            if (probe) return;

            log.log(LogLevel.INFO, "Setting app " + applicationInstanceReference.asString() + " to status " + applicationInstanceStatus);

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
        public void close()  {
            onLockRelease.run();
            try {
                lock.close();
            } catch (RuntimeException e) {
                // We may want to avoid logging some exceptions that may be expected, like when session expires.
                log.log(LogLevel.WARNING,
                        "Failed to close application lock for " +
                        ZookeeperStatusService.class.getSimpleName() + ", will ignore and continue",
                        e);
            }
        }
    }

}
