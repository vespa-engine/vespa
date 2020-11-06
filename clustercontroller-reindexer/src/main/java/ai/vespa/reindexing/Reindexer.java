// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing;

import ai.vespa.reindexing.Reindexing.Status;
import ai.vespa.reindexing.ReindexingCurator.ReindexingLockException;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorControlHandler.CompletionCode;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.vespa.curator.Lock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.documentapi.VisitorControlHandler.CompletionCode.ABORTED;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.joining;

/**
 * Progresses reindexing efforts by creating visitor sessions against its own content cluster,
 * which send documents straight to storage — via indexing if the documenet type has "index" mode.
 * The {@link #reindex} method blocks until interrupted, or until no more reindexing is left to do.
 *
 * @author jonmv
 */
public class Reindexer {

    private static final Logger log = Logger.getLogger(Reindexer.class.getName());

    private final Cluster cluster;
    private final Map<DocumentType, Instant> ready;
    private final ReindexingCurator database;
    private final DocumentAccess access;
    private final Clock clock;

    public Reindexer(Cluster cluster, Map<DocumentType, Instant> ready, ReindexingCurator database,
                     DocumentAccess access, Clock clock) {
        for (DocumentType type : ready.keySet())
            cluster.bucketOf(type); // Verifies this is known.

        this.cluster = cluster;
        this.ready = new TreeMap<>(ready); // Iterate through document types in consistent order.
        this.database = database;
        this.access = access;
        this.clock = clock;
    }

    /** Starts and tracks reprocessing of ready document types until done, or interrupted. */
    public void reindex() throws ReindexingLockException {
        try (Lock lock = database.lockReindexing()) {
            Reindexing reindexing = database.readReindexing();
            for (DocumentType type : ready.keySet()) { // We consider only document types for which we have config.
                if (ready.get(type).isAfter(clock.instant())) {
                    log.log(WARNING, "Received config for reindexing which is ready in the future — will process later " +
                                     "(" + ready.get(type) + " is after " + clock.instant() + ")");
                }
                else {
                    // If this is a new document type (or a new cluster), no reindexing is required.
                    Status status = reindexing.status().getOrDefault(type,
                                                                     Status.ready(clock.instant())
                                                                           .running()
                                                                           .successful(clock.instant()));
                    reindexing = reindexing.with(type, progress(type, status));
                }
                if (Thread.interrupted()) // Clear interruption status so blocking calls function normally again.
                    break;
            }
            database.writeReindexing(reindexing);
        }
    }

    @SuppressWarnings("fallthrough") // (ノಠ ∩ಠ)ノ彡( \o°o)\
    private Status progress(DocumentType type, Status status) {
        if (ready.get(type).isAfter(status.startedAt()))
            status = Status.ready(clock.instant()); // Need to restart, as a newer reindexing is required.

        switch (status.state()) {
            default:
                log.log(WARNING, "Unknown reindexing state '" + status.state() + "'");
            case FAILED:
                log.log(FINE, () -> "Not continuing reindexing of " + type + " due to previous failure");
            case SUCCESSFUL: // Intentional fallthrough — all three are done states.
                return status;
            case RUNNING:
                log.log(WARNING, "Unepxected state 'RUNNING' of reindexing of " + type);
            case READY: // Intentional fallthrough — must just assume we failed updating state when exiting previously.
            log.log(FINE, () -> "Running reindexing of " + type + ", which started at " + status.startedAt());
        }

        // Visit buckets until they're all done, or until we are interrupted.
        status = status.running();
        VisitorControlHandler control = new VisitorControlHandler();
        visit(type, status.progress().orElse(null), control);

        // Progress is null if no buckets were successfully visited due to interrupt.
        if (control.getProgress() != null)
            status = status.progressed(control.getProgress());

        // If we were interrupted, the result may not yet be set in the control handler.
        CompletionCode code = control.getResult() != null ? control.getResult().getCode() : ABORTED;
        switch (code) {
            default:
                log.log(WARNING, "Unexpected visitor result '" + control.getResult().getCode() + "'");
            case FAILURE: // Intentional fallthrough — this is an error.
                log.log(WARNING, "Visiting failed: " + control.getResult().getMessage());
                return status.failed(clock.instant(), control.getResult().getMessage());
            case ABORTED:
                log.log(FINE, () -> "Halting reindexing of " + type + " due to shutdown — will continue later");
                return status.halted();
            case SUCCESS:
                log.log(INFO, "Completed reindexing of " + type + " after " + Duration.between(status.startedAt(), clock.instant()));
                return status.successful(clock.instant());
        }
    }

    private void visit(DocumentType type, ProgressToken progress, VisitorControlHandler control) {
        VisitorParameters parameters = createParameters(type, progress);
        parameters.setControlHandler(control);
        VisitorSession session;
        try {
            session = access.createVisitorSession(parameters);
        }
        catch (ParseException e) {
            throw new IllegalStateException(e);
        }

        // Wait until done, or interrupted, in which case we abort the visit but don't wait for it to complete.
        try {
            control.waitUntilDone();
        }
        catch (InterruptedException e) {
            control.abort();
            Thread.currentThread().interrupt();
        }
        session.destroy(); // If thread is interrupted, this will not wait, but will retain the interrupted flag.
    }

    VisitorParameters createParameters(DocumentType type, ProgressToken progress) {
        VisitorParameters parameters = new VisitorParameters(type.getName());
        parameters.setRemoteDataHandler(cluster.name());
        parameters.setResumeToken(progress);
        parameters.setFieldSet(type.getName() + ":[document]");
        parameters.setPriority(DocumentProtocol.Priority.LOW_1);
        parameters.setRoute(cluster.route());
        parameters.setBucketSpace(cluster.bucketOf(type));
        // parameters.setVisitorLibrary("ReindexVisitor");
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

        String bucketOf(DocumentType documentType) {
            return requireNonNull(documentBuckets.get(documentType), "Unknown bucket for " + documentType);
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
