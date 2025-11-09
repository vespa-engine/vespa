// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.embedding.voyageai.VoyageAiEmbedderConfig;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.VoyageAIEmbedder;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VoyageAI embedder configuration parsing and validation.
 */
public class VoyageAIEmbedderTest {

    @Test
    void testVoyageAIEmbedderWithFullConfiguration() throws Exception {
        VespaModel model = loadModel(Path.fromString("src/test/cfg/application/voyageai-embedder/"));
        ApplicationContainerCluster cluster = model.getContainerClusters().get("container");

        Component<?, ?> component = cluster.getComponentsMap().get(new ComponentId("voyage-full"));
        assertNotNull(component, "VoyageAI embedder component should be present");
        assertTrue(component instanceof VoyageAIEmbedder, "Component should be VoyageAIEmbedder");

        VoyageAiEmbedderConfig config = getVoyageAIEmbedderConfig(cluster, "voyage-full");

        // Verify all configuration values
        assertEquals("voyage-3", config.model());
        assertEquals("voyage_api_key", config.apiKeySecretName());
        assertEquals("https://api.voyageai.com/v1/embeddings", config.endpoint());
        assertEquals(64, config.maxBatchSize());
        assertEquals(60000, config.timeout());
        assertEquals(5, config.maxRetries());
        assertEquals(VoyageAiEmbedderConfig.DefaultInputType.Enum.query, config.defaultInputType().value());
        assertFalse(config.autoDetectInputType());
        assertTrue(config.normalize());
        assertTrue(config.truncate());
        assertEquals(10, config.poolSize());
        assertEquals(2000, config.cacheSize());
    }

    @Test
    void testVoyageAIEmbedderWithMinimalConfiguration() throws Exception {
        VespaModel model = loadModel(Path.fromString("src/test/cfg/application/voyageai-embedder/"));
        ApplicationContainerCluster cluster = model.getContainerClusters().get("container");

        Component<?, ?> component = cluster.getComponentsMap().get(new ComponentId("voyage-minimal"));
        assertNotNull(component, "VoyageAI embedder component should be present");

        VoyageAiEmbedderConfig config = getVoyageAIEmbedderConfig(cluster, "voyage-minimal");

        // Verify required field
        assertEquals("voyage_key", config.apiKeySecretName());

        // Verify defaults are used
        assertEquals("voyage-3", config.model()); // Default model
        assertEquals("https://api.voyageai.com/v1/embeddings", config.endpoint()); // Default endpoint
        assertEquals(128, config.maxBatchSize()); // Default batch size
        assertEquals(30000, config.timeout()); // Default timeout
        assertEquals(3, config.maxRetries()); // Default retries
        assertEquals(VoyageAiEmbedderConfig.DefaultInputType.Enum.document, config.defaultInputType().value());
        assertTrue(config.autoDetectInputType()); // Default auto-detect
        assertFalse(config.normalize()); // Default no normalization
        assertTrue(config.truncate()); // Default truncate
        assertEquals(5, config.poolSize()); // Default pool size
        assertEquals(1000, config.cacheSize()); // Default cache size
    }

    @Test
    void testVoyageAIEmbedderMissingApiKey() {
        String servicesXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version="1.0">
                    <container id="container" version="1.0">
                        <component id="voyage" type="voyage-ai-embedder">
                            <model>voyage-3</model>
                        </component>
                    </container>
                </services>
                """;

        // Should fail because api-key-secret-name is required
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            buildModelFromXml(servicesXml);
        });
        assertTrue(exception.getMessage().contains("api-key-secret-name"));
    }

    @Test
    void testVoyageAIEmbedderInvalidBatchSize() {
        String servicesXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version="1.0">
                    <container id="container" version="1.0">
                        <component id="voyage" type="voyage-ai-embedder">
                            <api-key-secret-name>key</api-key-secret-name>
                            <max-batch-size>2000</max-batch-size>
                        </component>
                    </container>
                </services>
                """;

        // Should fail because batch size > 1000
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            buildModelFromXml(servicesXml);
        });
        assertTrue(exception.getMessage().contains("max-batch-size"));
    }

    @Test
    void testVoyageAIEmbedderInvalidTimeout() {
        String servicesXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version="1.0">
                    <container id="container" version="1.0">
                        <component id="voyage" type="voyage-ai-embedder">
                            <api-key-secret-name>key</api-key-secret-name>
                            <timeout>500</timeout>
                        </component>
                    </container>
                </services>
                """;

        // Should fail because timeout < 1000ms
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            buildModelFromXml(servicesXml);
        });
        assertTrue(exception.getMessage().contains("timeout"));
    }

    @Test
    void testVoyageAIEmbedderInvalidInputType() {
        String servicesXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version="1.0">
                    <container id="container" version="1.0">
                        <component id="voyage" type="voyage-ai-embedder">
                            <api-key-secret-name>key</api-key-secret-name>
                            <default-input-type>invalid</default-input-type>
                        </component>
                    </container>
                </services>
                """;

        // Should fail because input type must be 'query' or 'document'
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            buildModelFromXml(servicesXml);
        });
        assertTrue(exception.getMessage().contains("default-input-type"));
    }

    @Test
    void testVoyageAIEmbedderInvalidModelName() {
        String servicesXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version="1.0">
                    <container id="container" version="1.0">
                        <component id="voyage" type="voyage-ai-embedder">
                            <api-key-secret-name>key</api-key-secret-name>
                            <model>invalid-model-name</model>
                        </component>
                    </container>
                </services>
                """;

        // Should fail because model name must start with 'voyage'
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            buildModelFromXml(servicesXml);
        });
        assertTrue(exception.getMessage().contains("voyage"));
    }

    @Test
    void testMultipleVoyageAIEmbedders() throws Exception {
        VespaModel model = loadModel(Path.fromString("src/test/cfg/application/voyageai-embedder/"));
        ApplicationContainerCluster cluster = model.getContainerClusters().get("container");

        // Verify both embedders are present
        assertNotNull(cluster.getComponentsMap().get(new ComponentId("voyage-full")));
        assertNotNull(cluster.getComponentsMap().get(new ComponentId("voyage-minimal")));

        // Verify they have different configurations
        VoyageAiEmbedderConfig fullConfig = getVoyageAIEmbedderConfig(cluster, "voyage-full");
        VoyageAiEmbedderConfig minimalConfig = getVoyageAIEmbedderConfig(cluster, "voyage-minimal");

        assertNotEquals(fullConfig.apiKeySecretName(), minimalConfig.apiKeySecretName());
        assertNotEquals(fullConfig.maxBatchSize(), minimalConfig.maxBatchSize());
    }

    // ===== Helper Methods =====

    private VespaModel loadModel(Path path) throws Exception {
        return new VespaModelCreatorWithFilePkg(path.toFile()).create();
    }

    private VespaModel buildModelFromXml(String servicesXml) throws Exception {
        return new VespaModelCreatorWithFilePkg(servicesXml).create();
    }

    private VoyageAiEmbedderConfig getVoyageAIEmbedderConfig(ApplicationContainerCluster cluster, String componentId) {
        Component<?, ?> component = cluster.getComponentsMap().get(new ComponentId(componentId));
        assertNotNull(component, "Component " + componentId + " should exist");
        assertTrue(component instanceof VoyageAIEmbedder, "Component should be VoyageAIEmbedder");

        VoyageAIEmbedder embedder = (VoyageAIEmbedder) component;
        VoyageAiEmbedderConfig.Builder builder = new VoyageAiEmbedderConfig.Builder();
        embedder.getConfig(builder);
        return builder.build();
    }
}
