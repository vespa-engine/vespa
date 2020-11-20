// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing;

import ai.vespa.reindexing.Reindexing.Status;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.path.Path;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Reads and writes status of initiated reindexing jobs.
 *
 * @author jonmv
 */
public class ReindexingCurator {

    private static final String STATUS = "status";
    private static final String TYPE =  "type";
    private static final String STARTED_MILLIS = "startedMillis";
    private static final String ENDED_MILLIS = "endedMillis";
    private static final String PROGRESS = "progress";
    private static final String STATE = "state";
    private static final String MESSAGE = "message";

    private final Curator curator;
    private final String clusterName;
    private final ReindexingSerializer serializer;
    private final Duration lockTimeout;

    public ReindexingCurator(Curator curator, String clusterName, DocumentTypeManager manager) {
        this(curator, clusterName, manager, Duration.ofSeconds(1));
    }

    ReindexingCurator(Curator curator, String clusterName, DocumentTypeManager manager, Duration lockTimeout) {
        this.curator = curator;
        this.clusterName = clusterName;
        this.serializer = new ReindexingSerializer(manager);
        this.lockTimeout = lockTimeout;
    }

    public Reindexing readReindexing() {
        return curator.getData(statusPath()).map(serializer::deserialize)
                      .orElse(Reindexing.empty());
    }

    public void writeReindexing(Reindexing reindexing) {
        curator.set(statusPath(), serializer.serialize(reindexing));
    }

    /** This lock must be held to manipulate reindexing state, or by whoever has a running visitor. */
    public Lock lockReindexing() throws ReindexingLockException {
        try {
            return curator.lock(lockPath(), lockTimeout);
        }
        catch (UncheckedTimeoutException e) { // TODO jonmv: Avoid use of guava classes.
            throw new ReindexingLockException(e);
        }
    }

    private Path rootPath() { return Path.fromString("/reindexing/v1/" + clusterName); }
    private Path statusPath() { return rootPath().append("status"); }
    private Path lockPath() { return rootPath().append("lock"); }


    private static class ReindexingSerializer {

        private final DocumentTypeManager types;

        public ReindexingSerializer(DocumentTypeManager types) {
            this.types = types;
        }

        private byte[] serialize(Reindexing reindexing) {
            Cursor root = new Slime().setObject();
            Cursor statusArray = root.setArray(STATUS);
            reindexing.status().forEach((type, status) -> {
                Cursor statusObject = statusArray.addObject();
                statusObject.setString(TYPE, type.getName());
                statusObject.setLong(STARTED_MILLIS, status.startedAt().toEpochMilli());
                status.endedAt().ifPresent(endedAt -> statusObject.setLong(ENDED_MILLIS, endedAt.toEpochMilli()));
                status.progress().ifPresent(progress -> statusObject.setString(PROGRESS, progress.serializeToString()));
                statusObject.setString(STATE, toString(status.state()));
                status.message().ifPresent(message -> statusObject.setString(MESSAGE, message));
            });
            return Exceptions.uncheck(() -> SlimeUtils.toJsonBytes(root));
        }

        private Reindexing deserialize(byte[] json) {
            return new Reindexing(SlimeUtils.entriesStream(SlimeUtils.jsonToSlimeOrThrow(json).get().field(STATUS))
                                            .filter(object -> require(TYPE, object, field -> types.hasDataType(field.asString()))) // Forget unknown documents.
                                            .collect(toUnmodifiableMap(object -> require(TYPE, object, field -> types.getDocumentType(field.asString())),
                                                                       object -> new Status(require(STARTED_MILLIS, object, field -> Instant.ofEpochMilli(field.asLong())),
                                                                                            get(ENDED_MILLIS, object, field -> Instant.ofEpochMilli(field.asLong())),
                                                                                            get(PROGRESS, object, field -> ProgressToken.fromSerializedString(field.asString())),
                                                                                            require(STATE, object, field -> toState(field.asString())),
                                                                                            get(MESSAGE, object, field -> field.asString())))));
        }

        private static <T> T get(String name, Inspector object, Function<Inspector, T> mapper) {
            return object.field(name).valid() ? mapper.apply(object.field(name)) : null;
        }

        private static <T> T require(String name, Inspector object, Function<Inspector, T> mapper) {
            return requireNonNull(get(name, object, mapper));
        }

        private static String toString(Reindexing.State state) {
            switch (state) {
                case READY: return "ready";
                case RUNNING: return "running";
                case SUCCESSFUL: return "successful";
                case FAILED: return "failed";
                default: throw new IllegalArgumentException("Unexpected state '" + state + "'");
            }
        }

        private static Reindexing.State toState(String value) {
            switch (value) {
                case "ready": return Reindexing.State.READY;
                case "running": return Reindexing.State.RUNNING;
                case "successful": return Reindexing.State.SUCCESSFUL;
                case "failed": return Reindexing.State.FAILED;
                default: throw new IllegalArgumentException("Unknown state '" + value + "'");
            }
        }

    }

    /** Indicates that taking the reindexing lock failed within the alotted time. */
    static class ReindexingLockException extends Exception {

        ReindexingLockException(UncheckedTimeoutException cause) {
            super("Failed to obtain the reindexing lock", cause);
        }

    }

}
