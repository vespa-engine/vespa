// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing;

import ai.vespa.reindexing.Reindexer.Cluster;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.searchdefinition.derived.Deriver;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import com.yahoo.vespa.config.content.reindexing.ReindexingConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static ai.vespa.reindexing.ReindexingMaintainer.parseCluster;
import static ai.vespa.reindexing.ReindexingMaintainer.parseReady;
import static ai.vespa.reindexing.ReindexingMaintainer.scheduleStaggered;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author jonmv
 */
class ReindexingMaintainerTest {

    @Test
    void testParsing() {
        DocumentmanagerConfig musicConfig = Deriver.getDocumentManagerConfig("src/test/resources/schemas/music.sd").build();
        DocumentTypeManager manager = new DocumentTypeManager(musicConfig);

        assertEquals(Map.of(manager.getDocumentType("music"), Instant.ofEpochMilli(123)),
                     parseReady(new ReindexingConfig.Clusters.Builder()
                                        .documentTypes("music", new ReindexingConfig.Clusters.DocumentTypes.Builder().readyAtMillis(123))
                                        .build(),
                                manager));

        // Unknown document type fails
        assertThrows(NullPointerException.class,
                     () -> parseReady(new ReindexingConfig.Clusters.Builder()
                                              .documentTypes("poetry", new ReindexingConfig.Clusters.DocumentTypes.Builder().readyAtMillis(123))
                                              .build(),
                                      manager));

        assertEquals(new Cluster("cluster", Map.of(manager.getDocumentType("music"), "default")),
                     parseCluster("cluster",
                                  new ClusterListConfig.Builder()
                                          .storage(new ClusterListConfig.Storage.Builder()
                                                           .name("oyster")
                                                           .configid("configId"))
                                          .storage(new ClusterListConfig.Storage.Builder()
                                                           .name("cluster")
                                                           .configid("configId"))
                                          .build(),
                                  new AllClustersBucketSpacesConfig.Builder()
                                          .cluster("oyster", new AllClustersBucketSpacesConfig.Cluster.Builder()
                                                  .documentType("music", new AllClustersBucketSpacesConfig.Cluster.DocumentType.Builder()
                                                          .bucketSpace("global")))
                                          .cluster("cluster", new AllClustersBucketSpacesConfig.Cluster.Builder()
                                                  .documentType("music", new AllClustersBucketSpacesConfig.Cluster.DocumentType.Builder()
                                                          .bucketSpace("default")))
                                          .build(),
                                  manager));

        // Cluster missing in bucket space list fails.
        assertThrows(NullPointerException.class,
                     () -> parseCluster("cluster",
                                        new ClusterListConfig.Builder()
                                                .storage(new ClusterListConfig.Storage.Builder()
                                                                 .name("cluster")
                                                                 .configid("configId"))
                                                .build(),
                                        new AllClustersBucketSpacesConfig.Builder()
                                                .cluster("oyster", new AllClustersBucketSpacesConfig.Cluster.Builder()
                                                        .documentType("music", new AllClustersBucketSpacesConfig.Cluster.DocumentType.Builder()
                                                                .bucketSpace("global")))
                                                .build(),
                                        manager));

        // Cluster missing in cluster list fails.
        assertThrows(IllegalStateException.class,
                     () -> parseCluster("cluster",
                                        new ClusterListConfig.Builder()
                                                .storage(new ClusterListConfig.Storage.Builder()
                                                                 .name("oyster")
                                                                 .configid("configId"))
                                                .build(),
                                        new AllClustersBucketSpacesConfig.Builder()
                                                .cluster("cluster", new AllClustersBucketSpacesConfig.Cluster.Builder()
                                                        .documentType("music", new AllClustersBucketSpacesConfig.Cluster.DocumentType.Builder()
                                                                .bucketSpace("default")))
                                                .build(),
                                        manager));
    }

    @Test
    void testStaggering() {
        scheduleStaggered((delayMillis, intervalMillis) -> {
                              assertEquals(0, delayMillis);
                              assertEquals(10, intervalMillis);
                          },
                          Duration.ofMillis(10),
                          Instant.ofEpochMilli(27),
                          "host",
                          "nys:123,hark:123");

        scheduleStaggered((delayMillis, intervalMillis) -> {
                              assertEquals(3, delayMillis);
                              assertEquals(10, intervalMillis);
                          },
                          Duration.ofMillis(10),
                          Instant.ofEpochMilli(27),
                          "host",
                          "host:123");

        scheduleStaggered((delayMillis, intervalMillis) -> {
                              assertEquals(7, delayMillis);
                              assertEquals(20, intervalMillis);
                          },
                          Duration.ofMillis(10),
                          Instant.ofEpochMilli(13),
                          "host",
                          "host:123,:nys:321");

        scheduleStaggered((delayMillis, intervalMillis) -> {
                              assertEquals(17, delayMillis);
                              assertEquals(20, intervalMillis);
                          },
                          Duration.ofMillis(10),
                          Instant.ofEpochMilli(13),
                          "nys",
                          "host:123,nys:321");
    }

}
