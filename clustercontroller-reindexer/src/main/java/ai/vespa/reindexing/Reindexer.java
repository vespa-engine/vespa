// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing;

import ai.vespa.reindexing.Reindexing.Status;
import ai.vespa.reindexing.ReindexingCurator.ReindexingLockException;
import com.yahoo.document.DocumentType;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorControlHandler.CompletionCode;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.jdisc.Metric;
import com.yahoo.messagebus.DynamicThrottlePolicy;
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
 * which send documents straight to storage — via indexing if the document type has "index" mode.
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
             clock
        );
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

        database.initializeIfEmpty(cluster.name, ready, clock.instant());
    }

    /** Lets the reindexer abort any ongoing visit session, wait for it to complete normally, then exit. */
    public void shutdown() {
        phaser.forceTermination(); // All parties waiting on this phaser are immediately allowed to proceed.
    }

    /** Starts and tracks reprocessing of ready document types until done, or interrupted. */
    public void reindex() throws ReindexingLockException {
        if (phaser.isTerminated())
            throw new IllegalStateException("Already shut down");

        // Keep metrics in sync across cluster controller containers.
        AtomicReference<Reindexing> reindexing = new AtomicReference<>(database.readReindexing(cluster.name()));
        database.writeReindexing(reindexing.get(), cluster.name());
        metrics.dump(reindexing.get());

        try (Lock lock = database.lockReindexing(cluster.name())) {
            reindexing.set(updateWithReady(ready, reindexing.get(), clock.instant()));
            database.writeReindexing(reindexing.get(), cluster.name());
            metrics.dump(reindexing.get());

            for (DocumentType type : ready.keySet()) { // We consider only document types for which we have config.
                if (ready.get(type).isAfter(clock.instant()))
                    log.log(INFO, "Received config for reindexing which is ready in the future — will process later " +
                                  "(" + ready.get(type) + " is after " + clock.instant() + ")");
                else
                    progress(type, reindexing, new AtomicReference<>(reindexing.get().status().get(type)));

                if (phaser.isTerminated())
                    break;
            }
        }
    }

    static Reindexing updateWithReady(Map<DocumentType, Instant> ready, Reindexing reindexing, Instant now) {
        for (DocumentType type : ready.keySet()) { // We update only for document types for which we have config.
            if ( ! ready.get(type).isAfter(now)) {
                Status status = reindexing.status().getOrDefault(type, Status.ready(now));
                if (status.startedAt().isBefore(ready.get(type)))
                    status = Status.ready(now);

                reindexing = reindexing.with(type, status);
            }
        }
        return reindexing;
    }

    @SuppressWarnings("fallthrough") // (ノಠ ∩ಠ)ノ彡( \o°o)\
    private void progress(DocumentType type, AtomicReference<Reindexing> reindexing, AtomicReference<Status> status) {
        switch (status.get().state()) {
            default:
                log.log(WARNING, "Unknown reindexing state '" + status.get().state() + "'");
            case FAILED:
                log.log(FINE, () -> "Not continuing reindexing of " + type + " due to previous failure");
            case SUCCESSFUL: // Intentional fallthrough — all three are done states.
                return;
            case RUNNING:
                log.log(WARNING, "Unexpected state 'RUNNING' of reindexing of " + type);
                break;
            case READY:
                status.updateAndGet(Status::running);
        }

        // Visit buckets until they're all done, or until we are shut down.
        AtomicReference<Instant> progressLastStored = new AtomicReference<>(clock.instant());
        VisitorControlHandler control = new VisitorControlHandler() {
            @Override
            public void onProgress(ProgressToken token) {
                super.onProgress(token);
                status.updateAndGet(value -> value.progressed(token));
                if (progressLastStored.get().isBefore(clock.instant().minusSeconds(10))) {
                    progressLastStored.set(clock.instant());
                    database.writeReindexing(reindexing.updateAndGet(value -> value.with(type, status.get())), cluster.name());
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
        log.log(FINE, () -> "Running reindexing of " + type);

        // Wait until done; or until termination is forced, in which case we shut down the visitor session immediately.
        phaser.arriveAndAwaitAdvance(); // Synchronize with visitor completion.
        sessionShutdown.run();  // Shutdown aborts the session unless already complete, then waits for it to terminate normally.
                                // Only as a last resort will we be interrupted here, and the wait for outstanding replies terminate.

        CompletionCode result = control.getResult() != null ? control.getResult().getCode()
                                                            : CompletionCode.ABORTED;
        switch (result) {
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
        database.writeReindexing(reindexing.updateAndGet(value -> value.with(type, status.get())), cluster.name());
        metrics.dump(reindexing.get());
    }

    VisitorParameters createParameters(DocumentType type, ProgressToken progress) {
        VisitorParameters parameters = new VisitorParameters(type.getName());
        parameters.setThrottlePolicy(new DynamicThrottlePolicy().setWindowSizeIncrement(0.2)
                                                                .setWindowSizeDecrementFactor(5)
                                                                .setResizeRate(10)
                                                                .setMinWindowSize(1));
        parameters.setRemoteDataHandler(cluster.name());
        parameters.setMaxPending(32);
        parameters.setResumeToken(progress);
        parameters.setFieldSet(type.getName() + ":[document]");
        parameters.setPriority(DocumentProtocol.Priority.NORMAL_3);
        parameters.setRoute(cluster.route());
        parameters.setBucketSpace(cluster.bucketSpaceOf(type));
        parameters.setMaxBucketsPerVisitor(1);
        parameters.setVisitorLibrary("ReindexingVisitor");
        return parameters;
    }


    static class Cluster {

        private final String name;
        private final Map<DocumentType, String> documentBuckets;

        Cluster(String name, Map<DocumentType, String> documentBuckets) {
            this.name = requireNonNull(name);
            this.documentBuckets = Map.copyOf(documentBuckets);
        }

        String name() {
            return name;
        }

        String route() {
            return "[Content:cluster=" + name + "]";
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
                   documentBuckets.equals(cluster.documentBuckets);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, documentBuckets);
        }

        @Override
        public String toString() {
            return "Cluster{" +
                   "name='" + name + '\'' +
                   ", documentBuckets=" + documentBuckets +
                   '}';
        }

    }

}
