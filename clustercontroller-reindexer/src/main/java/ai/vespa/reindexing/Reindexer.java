// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing;

import ai.vespa.reindexing.Reindexing.Status;
import ai.vespa.reindexing.ReindexingCurator.ReindexingLockException;
import com.google.inject.Inject;
import com.yahoo.document.DocumentType;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.curator.Lock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Progresses reindexing efforts by creating visitor sessions against its own content cluster,
 * which send documents straight to storage — via indexing if the documenet type has "index" mode.
 * The {@link #reindex} method blocks until shutdown is called, or until no more reindexing is left to do.
 *
 * @author jonmv
 */
public class Reindexer {

    private static final Logger log = Logger.getLogger(Reindexer.class.getName());

    private final Cluster cluster;
    private final Map<DocumentType, Instant> ready;
    private final ReindexingCurator database;
    private final Function<VisitorParameters, Runnable> visitorSessions;
    private final ReindexingMetrics metrics;
    private final Clock clock;
    private final Phaser phaser = new Phaser(2); // Reindexer and visitor.

    @Inject
    public Reindexer(Cluster cluster, Map<DocumentType, Instant> ready, ReindexingCurator database,
                     DocumentAccess access, Metric metric, Clock clock) {
        this(cluster,
             ready,
             database,
             parameters -> {
                 try {
                     return access.createVisitorSession(parameters)::destroy;
                 }
                 catch (ParseException e) {
                     throw new IllegalStateException(e);
                 }
             },
             metric,
             clock);
    }

    Reindexer(Cluster cluster, Map<DocumentType, Instant> ready, ReindexingCurator database,
              Function<VisitorParameters, Runnable> visitorSessions, Metric metric, Clock clock) {
        for (DocumentType type : ready.keySet())
            cluster.bucketSpaceOf(type); // Verifies this is known.

        this.cluster = cluster;
        this.ready = new TreeMap<>(ready); // Iterate through document types in consistent order.
        this.database = database;
        this.visitorSessions = visitorSessions;
        this.metrics = new ReindexingMetrics(metric, cluster.name);
        this.clock = clock;
    }

    /** Lets the reindexer abort any ongoing visit session, wait for it to complete normally, then exit. */
    public void shutdown() {
        phaser.forceTermination(); // All parties waiting on this phaser are immediately allowed to proceed.
    }

    /** Starts and tracks reprocessing of ready document types until done, or interrupted. */
    public void reindex() throws ReindexingLockException {
        if (phaser.isTerminated())
            throw new IllegalStateException("Already shut down");

        try (Lock lock = database.lockReindexing()) {
            for (DocumentType type : ready.keySet()) { // We consider only document types for which we have config.
                if (ready.get(type).isAfter(clock.instant()))
                    log.log(INFO, "Received config for reindexing which is ready in the future — will process later " +
                                  "(" + ready.get(type) + " is after " + clock.instant() + ")");
                else
                    progress(type);

                if (phaser.isTerminated())
                    break;
            }
        }
    }

    @SuppressWarnings("fallthrough") // (ノಠ ∩ಠ)ノ彡( \o°o)\
    private void progress(DocumentType type) {
        // If this is a new document type (or a new cluster), no reindexing is required.
        AtomicReference<Reindexing> reindexing = new AtomicReference<>(database.readReindexing());
        AtomicReference<Status> status = new AtomicReference<>(reindexing.get().status().getOrDefault(type,
                                                                                                      Status.ready(clock.instant())
                                                                                                            .running()
                                                                                                            .successful(clock.instant())));
        // Need to restart if a newer indexing is required.
        status.updateAndGet(value -> ready.get(type).isAfter(value.startedAt()) ? Status.ready(clock.instant()) : value);

        database.writeReindexing(reindexing.updateAndGet(value -> value.with(type, status.get())));
        metrics.dump(reindexing.get());

        switch (status.get().state()) {
            default:
                log.log(WARNING, "Unknown reindexing state '" + status.get().state() + "'");
            case FAILED:
                log.log(FINE, () -> "Not continuing reindexing of " + type + " due to previous failure");
            case SUCCESSFUL: // Intentional fallthrough — all three are done states.
                return;
            case RUNNING:
                log.log(WARNING, "Unexpected state 'RUNNING' of reindexing of " + type);
            case READY: // Intentional fallthrough — must just assume we failed updating state when exiting previously.
            log.log(FINE, () -> "Running reindexing of " + type);
        }

        // Visit buckets until they're all done, or until we are interrupted.
        status.updateAndGet(Status::running);
        AtomicReference<Instant> progressLastStored = new AtomicReference<>(clock.instant());
        VisitorControlHandler control = new VisitorControlHandler() {
            @Override
            public void onProgress(ProgressToken token) {
                super.onProgress(token);
                status.updateAndGet(value -> value.progressed(token));
                if (progressLastStored.get().isBefore(clock.instant().minusSeconds(10))) {
                    progressLastStored.set(clock.instant());
                    database.writeReindexing(reindexing.updateAndGet(value -> value.with(type, status.get())));
                    metrics.dump(reindexing.get());
                }
            }
            @Override
            public void onDone(CompletionCode code, String message) {
                super.onDone(code, message);
                phaser.arriveAndAwaitAdvance(); // Synchronize with the reindexer control thread.
            }
        };

        VisitorParameters parameters = createParameters(type, status.get().progress().orElse(null));
        parameters.setControlHandler(control);
        Runnable sessionShutdown = visitorSessions.apply(parameters); // Also starts the visitor session.

        // Wait until done; or until termination is forced, in which we shut down the visitor session immediately.
        phaser.arriveAndAwaitAdvance(); // Synchronize with visitor completion.
        sessionShutdown.run(); // Shutdown aborts the session, then waits for it to terminate normally.

        switch (control.getResult().getCode()) {
            default:
                log.log(WARNING, "Unexpected visitor result '" + control.getResult().getCode() + "'");
            case FAILURE: // Intentional fallthrough — this is an error.
                log.log(WARNING, "Visiting failed: " + control.getResult().getMessage());
                status.updateAndGet(value -> value.failed(clock.instant(), control.getResult().getMessage()));
                break;
            case ABORTED:
                log.log(FINE, () -> "Halting reindexing of " + type + " due to shutdown — will continue later");
                status.updateAndGet(Status::halted);
                break;
            case SUCCESS:
                log.log(INFO, "Completed reindexing of " + type + " after " + Duration.between(status.get().startedAt(), clock.instant()));
                status.updateAndGet(value -> value.successful(clock.instant()));
        }
        database.writeReindexing(reindexing.updateAndGet(value -> value.with(type, status.get())));
        metrics.dump(reindexing.get());
    }

    VisitorParameters createParameters(DocumentType type, ProgressToken progress) {
        VisitorParameters parameters = new VisitorParameters(type.getName());
        parameters.setRemoteDataHandler(cluster.name());
        parameters.setResumeToken(progress);
        parameters.setFieldSet(type.getName() + ":[document]");
        parameters.setPriority(DocumentProtocol.Priority.NORMAL_3);
        parameters.setRoute(cluster.route());
        parameters.setBucketSpace(cluster.bucketSpaceOf(type));
        // parameters.setVisitorLibrary("ReindexVisitor"); // TODO jonmv: Use when ready, or perhaps an argument to the DumpVisitor is enough?
        return parameters;
    }


    static class Cluster {

        private final String name;
        private final String configId;
        private final Map<DocumentType, String> documentBuckets;

        Cluster(String name, String configId, Map<DocumentType, String> documentBuckets) {
            this.name = requireNonNull(name);
            this.configId = requireNonNull(configId);
            this.documentBuckets = Map.copyOf(documentBuckets);
        }

        String name() {
            return name;
        }

        String route() {
            return "[Storage:cluster=" + name + ";clusterconfigid=" + configId + "]";
        }

        String bucketSpaceOf(DocumentType documentType) {
            return requireNonNull(documentBuckets.get(documentType), "Unknown bucket space for " + documentType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Cluster cluster = (Cluster) o;
            return name.equals(cluster.name) &&
                   configId.equals(cluster.configId) &&
                   documentBuckets.equals(cluster.documentBuckets);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, configId, documentBuckets);
        }

        @Override
        public String toString() {
            return "Cluster{" +
                   "name='" + name + '\'' +
                   ", configId='" + configId + '\'' +
                   ", documentBuckets=" + documentBuckets +
                   '}';
        }

    }

}
