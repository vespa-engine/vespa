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
import com.yahoo.vespa.curator.recipes.CuratorCounter;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.data.Stat;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
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
    final static String HOST_STATUS_CACHE_COUNTER_PATH = "/vespa/host-status-service-cache-counter";

    private final Curator curator;
    private final CuratorCounter counter;
    private final Metric metric;
    private final Timer timer;

    /**
     * A cache of metric contexts for each possible dimension map. In practice, there is one dimension map
     * for each application, so up to hundreds of elements.
     */
    private final ConcurrentHashMap<Map<String, String>, Metric.Context> cachedContexts = new ConcurrentHashMap<>();

    /** A cache of hosts allowed to be down. Access only through {@link #getValidCache()}! */
    private final Map<ApplicationInstanceReference, Set<HostName>> hostsDown = new ConcurrentHashMap<>();

    private volatile long cacheRefreshedAt;

    @Inject
    public ZookeeperStatusService(@Component Curator curator, @Component Metric metric, @Component Timer timer) {
        this.curator = curator;
        this.counter = new CuratorCounter(curator, HOST_STATUS_CACHE_COUNTER_PATH);
        this.cacheRefreshedAt = counter.get();
        this.metric = metric;
        this.timer = timer;
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
     * by other users of the cache. Since this function is backed by the cache, any such invalidations
     * will be reflected in the returned mapping; all users of the cache collaborate in repopulating it.
     */
    @Override
    public Function<ApplicationInstanceReference, Set<HostName>> getSuspendedHostsByApplication() {
        Map<ApplicationInstanceReference, Set<HostName>> suspendedHostsByApplication = getValidCache();
        return application -> suspendedHostsByApplication.computeIfAbsent(application, this::hostsDownFor);
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

    private void setHostStatus(ApplicationInstanceReference applicationInstanceReference,
                               HostName hostName,
                               HostStatus status) {
        String path = hostAllowedDownPath(applicationInstanceReference, hostName);

        boolean invalidate = false;
        try {
            switch (status) {
                case NO_REMARKS:
                    invalidate = deleteNode_ignoreNoNodeException(path, "Host already has state NO_REMARKS, path = " + path);
                    break;
                case ALLOWED_TO_BE_DOWN:
                    invalidate = createNode_ignoreNodeExistsException(path, "Host already has state ALLOWED_TO_BE_DOWN, path = " + path);
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected status '" + status + "'.");
            }
        } catch (Exception e) {
            invalidate = true;
            throw new RuntimeException(e);
        }
        finally {
            if (invalidate) {
                counter.next();
                hostsDown.remove(applicationInstanceReference);
            }
        }
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

    @Override
    public HostStatus getHostStatus(ApplicationInstanceReference applicationInstanceReference, HostName hostName) {
        return getValidCache().computeIfAbsent(applicationInstanceReference, this::hostsDownFor)
                              .contains(hostName) ? HostStatus.ALLOWED_TO_BE_DOWN : HostStatus.NO_REMARKS;
    }

    /** Holding an application's lock ensures the cache is up to date for that application. */
    private Map<ApplicationInstanceReference, Set<HostName>> getValidCache() {
        long cacheGeneration = counter.get();
        if (counter.get() != cacheRefreshedAt) {
            cacheRefreshedAt = cacheGeneration;
            hostsDown.clear();
        }
        return hostsDown;
    }

    private Set<HostName> hostsDownFor(ApplicationInstanceReference application) {
        try {
            if (curator.framework().checkExists().forPath(hostsAllowedDownPath(application)) == null)
                return Collections.emptySet();

            return curator.framework().getChildren().forPath(hostsAllowedDownPath(application))
                          .stream().map(HostName::new)
                          .collect(Collectors.toUnmodifiableSet());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private static String applicationInstancePath(ApplicationInstanceReference applicationInstanceReference) {
        return HOST_STATUS_BASE_PATH + '/' +
                applicationInstanceReference.tenantId() + ":" + applicationInstanceReference.applicationInstanceId();
    }

    private static String hostsAllowedDownPath(ApplicationInstanceReference applicationInstanceReference) {
        return applicationInstancePath(applicationInstanceReference) + "/hosts-allowed-down";
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
        public HostStatus getHostStatus(HostName hostName) {
            return ZookeeperStatusService.this.getHostStatus(applicationInstanceReference, hostName);
        }

        @Override
        public Set<HostName> getSuspendedHosts() {
            return getValidCache().computeIfAbsent(applicationInstanceReference, ZookeeperStatusService.this::hostsDownFor);
        }

        @Override
        public void setHostState(final HostName hostName, final HostStatus status) {
            if (probe) return;
            log.log(LogLevel.INFO, "Setting host " + hostName + " to status " + status);
            setHostStatus(applicationInstanceReference, hostName, status);
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
