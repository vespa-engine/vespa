// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing;

import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.schema.derived.Deriver;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
class ReindexingCuratorTest {

    @Test
    void testSerialization() {
        DocumentmanagerConfig musicConfig = Deriver.getDocumentManagerConfig("src/test/resources/schemas/music.sd").build();
        DocumentmanagerConfig emptyConfig = new DocumentmanagerConfig.Builder().build();
        DocumentTypeManager manager = new DocumentTypeManager(musicConfig);
        DocumentType music = manager.getDocumentType("music");
        MockCurator mockCurator = new MockCurator();
        ReindexingCurator curator = new ReindexingCurator(mockCurator, manager);

        assertEquals(Reindexing.empty(), curator.readReindexing("cluster"));

        Reindexing.Status status = Reindexing.Status.ready(Instant.ofEpochMilli(123))
                                                    .running()
                                                    .progressed(new ProgressToken());
        Reindexing reindexing = Reindexing.empty().with(music, status);
        curator.writeReindexing(reindexing, "cluster");
        assertEquals(reindexing, curator.readReindexing("cluster"));

        status = status.halted().running().failed(Instant.ofEpochMilli(321), "error");
        reindexing = reindexing.with(music, status);
        curator.writeReindexing(reindexing, "cluster");
        assertEquals(reindexing, curator.readReindexing("cluster"));

        // Unknown document types are forgotten.
        assertEquals(Reindexing.empty(), new ReindexingCurator(mockCurator, new DocumentTypeManager(emptyConfig)).readReindexing("cluster"));
    }

}
