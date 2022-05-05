// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing;

import ai.vespa.reindexing.Reindexer.Cluster;
import ai.vespa.reindexing.Reindexing.Trigger;
import ai.vespa.reindexing.ReindexingCurator.ReindexingLockException;
import com.yahoo.component.annotation.Inject;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.jdisc.Metric;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import com.yahoo.vespa.config.content.reindexing.ReindexingConfig;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.zookeeper.VespaZooKeeperServer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * Runs in all cluster controller containers, and progresses reindexing efforts.
 * Work is only done by one container at a time, by requiring a shared ZooKeeper lock to be held while visiting.
 * Whichever maintainer gets the lock holds it until all reindexing is done, or until shutdown.
 *
 * @author jonmv
 */
public class ReindexingMaintainer extends AbstractComponent {

    private static final Logger log = Logger.getLogger(Reindexing.class.getName());

    private final Curator curator;
    private final List<Reindexer> reindexers;
    private final ScheduledExecutorService executor;

    @Inject
    public ReindexingMaintainer(@SuppressWarnings("unused") VespaZooKeeperServer ensureZkHasStarted,
                                Metric metric,
                                DocumentAccess access, ZookeepersConfig zookeepersConfig,
                                ClusterListConfig clusterListConfig, AllClustersBucketSpacesConfig allClustersBucketSpacesConfig,
                                ReindexingConfig reindexingConfig) {
        this(Clock.systemUTC(), metric, access, zookeepersConfig, clusterListConfig, allClustersBucketSpacesConfig, reindexingConfig);
    }

    ReindexingMaintainer(Clock clock, Metric metric, DocumentAccess access, ZookeepersConfig zookeepersConfig,
                         ClusterListConfig clusterListConfig, AllClustersBucketSpacesConfig allClustersBucketSpacesConfig,
                         ReindexingConfig reindexingConfig) {
        this.curator = Curator.create(zookeepersConfig.zookeeperserverlist());
        ReindexingCurator reindexingCurator = new ReindexingCurator(curator, access.getDocumentTypeManager());
        this.reindexers = reindexingConfig.clusters().entrySet().stream()
                                          .map(cluster -> new Reindexer(parseCluster(cluster.getKey(), clusterListConfig, allClustersBucketSpacesConfig, access.getDocumentTypeManager()),
                                                                        parseReady(cluster.getValue(), access.getDocumentTypeManager()),
                                                                        reindexingCurator,
                                                                        access,
                                                                        metric,
                                                                        clock))
                                          .collect(toUnmodifiableList());
        this.executor = new ScheduledThreadPoolExecutor(reindexingConfig.clusters().size(), new DaemonThreadFactory("reindexer-"));
        if (reindexingConfig.enabled())
            scheduleStaggered((delayMillis, intervalMillis) -> executor.scheduleAtFixedRate(this::maintain, delayMillis, intervalMillis, TimeUnit.MILLISECONDS),
                              Duration.ofMinutes(1), clock.instant(), HostName.getLocalhost(), zookeepersConfig.zookeeperserverlist());
    }

    private void maintain() {
        for (Reindexer reindexer : reindexers)
            executor.submit(() -> {
                try {
                    reindexer.reindex();
                }
                catch (ReindexingLockException e) {
                    log.log(FINE, "Failed to acquire reindexing lock");
                }
                catch (Exception e) {
                    log.log(WARNING, "Exception when reindexing", e);
                }
            });
    }

    @Override
    public void deconstruct() {
        try {
            for (Reindexer reindexer : reindexers)
                reindexer.shutdown();

            executor.shutdown();

            executor.awaitTermination(5, TimeUnit.SECONDS); // Give it 5s to complete gracefully.

            curator.close(); // Close the underlying curator independently to force shutdown.

            if ( ! executor.isShutdown() && ! executor.awaitTermination(5, TimeUnit.SECONDS))
                log.log(WARNING, "Failed to shut down reindexing within timeout");
        }
        catch (InterruptedException e) {
            log.log(WARNING, "Interrupted while waiting for reindexing to shut down");
            Thread.currentThread().interrupt();
        }
        if ( ! executor.isShutdown()) {
            List<Runnable> remaining = executor.shutdownNow();
            log.log(WARNING, "Number of tasks remaining at hard shutdown: " + remaining.size());
        }

    }

    static List<Trigger> parseReady(ReindexingConfig.Clusters cluster, DocumentTypeManager manager) {
        return cluster.documentTypes().entrySet().stream()
                      .map(typeStatus -> new Trigger(manager.getDocumentType(typeStatus.getKey()),
                                                     Instant.ofEpochMilli(typeStatus.getValue().readyAtMillis()),
                                                     typeStatus.getValue().speed()))
                      .collect(toUnmodifiableList());
    }

    /** Schedules a task with the given interval (across all containers in this ZK cluster). */
    static void scheduleStaggered(BiConsumer<Long, Long> scheduler,
                                  Duration interval, Instant now,
                                  String hostname, String clusterHostnames) {
        long delayMillis = 0;
        long intervalMillis = interval.toMillis();
        List<String> hostnames = Stream.of(clusterHostnames.split(","))
                                       .map(hostPort -> hostPort.split(":")[0])
                                       .collect(toList());
        if (hostnames.contains(hostname)) {
            long offset = hostnames.indexOf(hostname) * intervalMillis;
            intervalMillis *= hostnames.size();
            delayMillis = Math.floorMod(offset - now.toEpochMilli(), intervalMillis);
        }
        scheduler.accept(delayMillis, intervalMillis);
    }

    static Cluster parseCluster(String name, ClusterListConfig clusters, AllClustersBucketSpacesConfig bucketSpaces,
                                DocumentTypeManager manager) {
        return clusters.storage().stream()
                       .filter(storage -> storage.name().equals(name))
                       .map(storage -> new Cluster(name,
                                                   bucketSpaces.cluster(name)
                                                               .documentType().entrySet().stream()
                                                               .collect(toMap(entry -> manager.getDocumentType(entry.getKey()),
                                                                         entry -> entry.getValue().bucketSpace()))))
                       .findAny()
                       .orElseThrow(() -> new IllegalStateException("This cluster (" + name + ") not among the list of clusters"));
    }

}
