// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing;

import ai.vespa.reindexing.Reindexer.Cluster;
import ai.vespa.reindexing.Reindexing.Status;
import ai.vespa.reindexing.ReindexingCurator.ReindexingLockException;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.searchdefinition.derived.Deriver;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author jonmv
 */
class ReindexerTest {

    static final Function<VisitorParameters, Runnable> failIfCalled = __ -> () -> { fail("Not supposed to run"); };

    final DocumentmanagerConfig musicConfig = Deriver.getDocumentManagerConfig("src/test/resources/schemas/music.sd").build();
    final DocumentTypeManager manager = new DocumentTypeManager(musicConfig);
    final DocumentType music = manager.getDocumentType("music");
    final Document document1 = new Document(music, "id:ns:music::one");
    final Cluster cluster = new Cluster("cluster", "id", Map.of(music, "default"));
    final ManualClock clock = new ManualClock(Instant.EPOCH);

    ReindexingCurator database;

    @BeforeEach
    void setUp() {
        database = new ReindexingCurator(new MockCurator(), "cluster", manager, Duration.ofMillis(1));
    }

    @Test
    void throwsWhenUnknownBuckets() {
        assertThrows(NullPointerException.class,
                     () -> new Reindexer(new Cluster("cluster", "id", Map.of()),
                                         Map.of(music, Instant.EPOCH),
                                         database,
                                         failIfCalled,
                                         clock));
    }

    @Test
    void throwsWhenLockHeldElsewhere() throws InterruptedException, ExecutionException {
        Reindexer reindexer = new Reindexer(cluster, Map.of(music, Instant.EPOCH), database, failIfCalled, clock);
        Executors.newSingleThreadExecutor().submit(database::lockReindexing).get();
        assertThrows(ReindexingLockException.class, reindexer::reindex);
    }

    @Test
    @Timeout(10)
    void nothingToDoWithEmptyConfig() throws ReindexingLockException {
        new Reindexer(cluster, Map.of(), database, failIfCalled, clock).reindex();
    }

    @Test
    void testParameters() {
        Reindexer reindexer = new Reindexer(cluster, Map.of(), database, failIfCalled, clock);
        ProgressToken token = new ProgressToken();
        VisitorParameters parameters = reindexer.createParameters(music, token);
        assertEquals("music:[document]", parameters.getFieldSet());
        assertSame(token, parameters.getResumeToken());
        assertEquals("default", parameters.getBucketSpace());
        assertEquals("[Storage:cluster=cluster;clusterconfigid=id]", parameters.getRoute().toString());
        assertEquals("cluster", parameters.getRemoteDataHandler());
        assertEquals("music", parameters.getDocumentSelection());
        assertEquals(DocumentProtocol.Priority.NORMAL_3, parameters.getPriority());
    }

    @Test
    @Timeout(10)
    void testReindexing() throws ReindexingLockException {
        // Reindexer is told to update "music" documents no earlier than EPOCH, which is just now.
        // Since "music" is a new document type, it is stored as just reindexed, and nothing else happens.
        new Reindexer(cluster, Map.of(music, Instant.EPOCH), database, failIfCalled, clock).reindex();
        Reindexing reindexing = Reindexing.empty().with(music, Status.ready(Instant.EPOCH).running().successful(Instant.EPOCH));
        assertEquals(reindexing, database.readReindexing());

        // New config tells reindexer to reindex "music" documents no earlier than at 10 millis after EPOCH, which isn't yet.
        // Nothing happens, since it's not yet time. This isn't supposed to happen unless high clock skew.
        clock.advance(Duration.ofMillis(5));
        new Reindexer(cluster, Map.of(music, Instant.ofEpochMilli(10)), database, failIfCalled, clock).reindex();
        assertEquals(reindexing, database.readReindexing());

        // It's time to reindex the "music" documents — let this complete successfully.
        clock.advance(Duration.ofMillis(10));
        AtomicBoolean shutDown = new AtomicBoolean();
        Executor executor = Executors.newSingleThreadExecutor();
        new Reindexer(cluster, Map.of(music, Instant.ofEpochMilli(10)), database, parameters -> {
            database.writeReindexing(Reindexing.empty()); // Wipe database to verify we write data from reindexer.
            executor.execute(() -> parameters.getControlHandler().onDone(VisitorControlHandler.CompletionCode.SUCCESS, "OK"));
            return () -> shutDown.set(true);
        }, clock).reindex();
        reindexing = reindexing.with(music, Status.ready(clock.instant()).running().successful(clock.instant()));
        assertEquals(reindexing, database.readReindexing());
        assertTrue(shutDown.get(), "Session was shut down");

        // One more reindexing, this time shut down before visit completes, but after progress is reported.
        clock.advance(Duration.ofMillis(10));
        shutDown.set(false);
        AtomicReference<Reindexer> aborted = new AtomicReference<>();
        aborted.set(new Reindexer(cluster, Map.of(music, Instant.ofEpochMilli(20)), database, parameters -> {
            database.writeReindexing(Reindexing.empty()); // Wipe database to verify we write data from reindexer.
            parameters.getControlHandler().onProgress(new ProgressToken());
            aborted.get().shutdown();
            return () -> {
                shutDown.set(true);
                parameters.getControlHandler().onDone(VisitorControlHandler.CompletionCode.ABORTED, "Shut down");
            };
        }, clock));
        aborted.get().reindex();
        reindexing = reindexing.with(music, Status.ready(clock.instant()).running().progressed(new ProgressToken()).halted());
        assertEquals(reindexing, database.readReindexing());
        assertTrue(shutDown.get(), "Session was shut down");

        // Last reindexing fails.
        clock.advance(Duration.ofMillis(10));
        shutDown.set(false);
        new Reindexer(cluster, Map.of(music, Instant.ofEpochMilli(30)), database, parameters -> {
            database.writeReindexing(Reindexing.empty()); // Wipe database to verify we write data from reindexer.
            executor.execute(() -> parameters.getControlHandler().onDone(VisitorControlHandler.CompletionCode.FAILURE, "Error"));
            return () -> shutDown.set(true);
        }, clock).reindex();
        reindexing = reindexing.with(music, Status.ready(clock.instant()).running().failed(clock.instant(), "Error"));
        assertEquals(reindexing, database.readReindexing());
        assertTrue(shutDown.get(), "Session was shut down");

        // Document type is ignored in next run, as it has failed fatally.
        new Reindexer(cluster, Map.of(music, Instant.ofEpochMilli(30)), database, failIfCalled, clock).reindex();
        assertEquals(reindexing, database.readReindexing());
    }

}
