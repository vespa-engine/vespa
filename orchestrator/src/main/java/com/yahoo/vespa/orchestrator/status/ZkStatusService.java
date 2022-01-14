// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.path.Path;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import com.yahoo.vespa.service.monitor.AntiServiceMonitor;
import com.yahoo.vespa.service.monitor.CriticalRegion;
import org.apache.zookeeper.data.Stat;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores instance suspension status and which hosts are allowed to go down in zookeeper.
 *
 * TODO: expiry of old application instances
 * @author Tony Vaagenes
 */
public class ZkStatusService implements StatusService {

    private static final Logger log = Logger.getLogger(ZkStatusService.class.getName());

    final static String HOST_STATUS_BASE_PATH = "/vespa/host-status-service";
    final static String APPLICATION_STATUS_BASE_PATH = "/vespa/application-status-service";

    private final Curator curator;
    private final HostInfosCache hostInfosCache;
    private final Metric metric;
    private final Timer timer;
    private final AntiServiceMonitor antiServiceMonitor;

    /**
     * A cache of metric contexts for each possible dimension map. In practice, there is one dimension map
     * for each application, so up to hundreds of elements.
     */
    private final ConcurrentHashMap<Map<String, String>, Metric.Context> cachedContexts = new ConcurrentHashMap<>();

    @Inject
    public ZkStatusService(Curator curator, Metric metric, Timer timer, AntiServiceMonitor antiServiceMonitor) {
        this(curator, metric, timer,
             new HostInfosCache(curator, new HostInfosServiceImpl(curator, timer)),
             antiServiceMonitor);
    }

    /** Non-private for testing only. */
    ZkStatusService(Curator curator, Metric metric, Timer timer, HostInfosCache hostInfosCache,
                    AntiServiceMonitor antiServiceMonitor) {
        this.curator = curator;
        this.metric = metric;
        this.timer = timer;
        this.hostInfosCache = hostInfosCache;
        this.antiServiceMonitor = antiServiceMonitor;
    }

    @Override
    public Set<ApplicationInstanceReference> getAllSuspendedApplications() {
        try {
            Set<ApplicationInstanceReference> resultSet = new HashSet<>();

            // Return empty set if the base path does not exist
            Stat stat = curator.framework().checkExists().forPath(APPLICATION_STATUS_BASE_PATH);
            if (stat == null) return resultSet;

            // The path exist and we may have children
            for (String referenceString : curator.framework().getChildren().forPath(APPLICATION_STATUS_BASE_PATH)) {
                ApplicationInstanceReference reference = OrchestratorUtil.parseApplicationInstanceReference(referenceString);
                resultSet.add(reference);
            }

            return resultSet;
        } catch (Exception e) {
            log.log(Level.FINE, "Something went wrong while listing out applications in suspend.", e);
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
    public ApplicationLock lockApplication(OrchestratorContext context, ApplicationInstanceReference reference)
            throws UncheckedTimeoutException {

        Runnable onRegistryClose;

        // A multi-application operation, aka batch suspension, will first issue a probe
        // then a non-probe. With "large locks", the lock is not released in between -
        // no lock is taken on the non-probe. Instead, the release is done on the multi-application
        // context close.
        if (context.hasLock(reference)) {
            onRegistryClose = () -> {};
        } else {
            Runnable unlock = acquireLock(context, reference);
            if (context.registerLockAcquisition(reference, unlock)) {
                onRegistryClose = () -> {};
            } else {
                onRegistryClose = unlock;
            }
        }

        try {
            return new ZkApplicationLock(
                    this,
                    curator,
                    onRegistryClose,
                    reference,
                    context.isProbe(),
                    hostInfosCache);
        } catch (Throwable t) {
            // In case the constructor throws an exception.
            onRegistryClose.run();
            throw t;
        }
    }

    private Runnable acquireLock(OrchestratorContext context, ApplicationInstanceReference reference)
            throws UncheckedTimeoutException {
        ApplicationId applicationId = OrchestratorUtil.toApplicationId(reference);
        String app = applicationId.application().value() + "." + applicationId.instance().value();
        Map<String, String> dimensions = Map.of(
                "tenantName", applicationId.tenant().value(),
                "applicationId", applicationId.toFullString(),
                "app", app);
        Metric.Context metricContext = cachedContexts.computeIfAbsent(dimensions, metric::createContext);

        Duration duration = context.getTimeLeft();
        String lockPath = applicationInstanceLock2Path(reference);
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
            // TODO: These metrics are redundant with Lock's metrics
            metric.set("orchestrator.lock.acquire-latency", seconds, metricContext);
            metric.set("orchestrator.lock.acquired", lockAcquired ? 1 : 0, metricContext);

            metric.add("orchestrator.lock.acquire", 1, metricContext);
            String acquireResultMetricName = lockAcquired ? "orchestrator.lock.acquire-success" : "orchestrator.lock.acquire-timedout";
            metric.add(acquireResultMetricName, 1, metricContext);
        }

        CriticalRegion inaccessibleDuperModelRegion = antiServiceMonitor
                .disallowDuperModelLockAcquisition(ZkStatusService.class.getSimpleName() + " application lock");

        return () -> {
            try {
                lock.close();
            } catch (RuntimeException e) {
                // We may want to avoid logging some exceptions that may be expected, like when session expires.
                log.log(Level.WARNING,
                        "Failed to close application lock for " +
                                ZkStatusService.class.getSimpleName() + ", will ignore and continue",
                        e);
            }

            inaccessibleDuperModelRegion.close();

            Instant lockReleasedTime = timer.currentTime();
            double seconds = durationInSeconds(acquireEndTime, lockReleasedTime);
            metric.set("orchestrator.lock.hold-latency", seconds, metricContext);
        };
    }

    private double durationInSeconds(Instant startInstant, Instant endInstant) {
        return Duration.between(startInstant, endInstant).toMillis() / 1000.0;
    }

    @Override
    public HostInfo getHostInfo(ApplicationInstanceReference reference, HostName hostName) {
        return hostInfosCache.getHostInfos(reference).getOrNoRemarks(hostName);
    }

    @Override
    public ApplicationInstanceStatus getApplicationInstanceStatus(ApplicationInstanceReference reference) {
        try {
            Stat statOrNull = curator.framework().checkExists().forPath(
                    applicationInstanceSuspendedPath(reference));

            return (statOrNull == null) ? ApplicationInstanceStatus.NO_REMARKS : ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Remove all host-related data in ZooKeeper for all hostnames outside the given set.
     */
    @Override
    public void onApplicationActivate(ApplicationInstanceReference reference, Set<HostName> hostnames) {
        withLockForAdminOp(reference, " was activated", () -> {
            HostInfos hostInfos = hostInfosCache.getCachedHostInfos(reference);
            Set<HostName> toRemove = new HashSet<>(hostInfos.getZkHostnames());
            toRemove.removeAll(hostnames);
            if (toRemove.size() > 0) {
                hostInfosCache.removeHosts(reference, toRemove);
            }
        });
    }

    /**
     * Remove the application from ZooKeeper.
     *
     * <ol>
     *     <li>/vespa/host-status/APPLICATION_ID (should just be ./hosts/*)</li>
     *     <li>/vespa/host-status-service/REFERENCE/hosts-allowed-down  (should just be ./*)</li>
     *     <li>/vespa/application-status-service/REFERENCE  (should just be .)</li>
     * </ol>
     */
    @Override
    public void onApplicationRemove(ApplicationInstanceReference reference) {
        withLockForAdminOp(reference, " was removed", () -> {
            // /vespa/application-status-service/REFERENCE
            curator.delete(Path.fromString(applicationInstanceSuspendedPath(reference)));

            // /vespa/host-status-service/REFERENCE/hosts-allowed-down
            curator.delete(Path.fromString(hostsAllowedDownPath(reference)));

            // /vespa/host-status/APPLICATION_ID
            hostInfosCache.removeApplication(reference);
        });
    }

    private void withLockForAdminOp(ApplicationInstanceReference reference,
                                    String eventDescription,
                                    Runnable runnable) {
        OrchestratorContext context = OrchestratorContext.createContextForAdminOp(timer.toUtcClock());

        final ApplicationLock lock;
        try {
            lock = lockApplication(context, reference);
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Failed to get Orchestrator lock on when " + reference +
                    eventDescription + ": " + e.getMessage());
            return;
        }

        try (lock) {
            runnable.run();
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Failed to clean up after " + reference + eventDescription +
                    ": " + e.getMessage());
        }
    }

    static String applicationInstanceReferencePath(ApplicationInstanceReference reference) {
        return HOST_STATUS_BASE_PATH + '/' + reference.asString();
    }

    private static String hostsAllowedDownPath(ApplicationInstanceReference reference) {
        return applicationInstanceReferencePath(reference) + "/hosts-allowed-down";
    }

    private static String applicationInstanceLock2Path(ApplicationInstanceReference reference) {
        return applicationInstanceReferencePath(reference) + "/lock2";
    }

    String applicationInstanceSuspendedPath(ApplicationInstanceReference reference) {
        return APPLICATION_STATUS_BASE_PATH + "/" + OrchestratorUtil.toRestApiFormat(reference);
    }

    private static String hostAllowedDownPath(ApplicationInstanceReference reference, HostName hostname) {
        return hostsAllowedDownPath(reference) + '/' + hostname.s();
    }

}
