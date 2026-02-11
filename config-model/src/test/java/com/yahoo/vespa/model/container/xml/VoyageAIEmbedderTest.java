// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.VoyageAIEmbedder;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertInstanceOf(VoyageAIEmbedder.class, component, "Component should be VoyageAIEmbedder");

        VoyageAiEmbedderConfig config = getVoyageAIEmbedderConfig(cluster, "voyage-full");

        // Verify all configuration values
        assertEquals("voyage-3.5", config.model());
        assertEquals("voyage_api_key", config.apiKeySecretRef());
        assertEquals(1024, config.dimensions());
        assertEquals("https://api.voyageai.com/v1/embeddings", config.endpoint());
        assertTrue(config.truncate());
    }

    @Test
    void testVoyageAIEmbedderWithMinimalConfiguration() throws Exception {
        VespaModel model = loadModel(Path.fromString("src/test/cfg/application/voyageai-embedder/"));
        ApplicationContainerCluster cluster = model.getContainerClusters().get("container");

        Component<?, ?> component = cluster.getComponentsMap().get(new ComponentId("voyage-minimal"));
        assertNotNull(component, "VoyageAI embedder component should be present");

        VoyageAiEmbedderConfig config = getVoyageAIEmbedderConfig(cluster, "voyage-minimal");

        // Verify required fields
        assertEquals("voyage_key", config.apiKeySecretRef());
        assertEquals("voyage-3.5", config.model());
        assertEquals(1024, config.dimensions());
        assertEquals("https://api.voyageai.com/v1/embeddings", config.endpoint()); // Default endpoint
        assertEquals(3, config.maxRetries()); // Default retries
        assertTrue(config.truncate()); // Default truncate
    }

    @Test
    void testVoyageAIEmbedderInvalidModelName() {
        String servicesXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version="1.0">
                    <container id="container" version="1.0">
                        <component id="voyage" type="voyage-ai-embedder">
                            <api-key-secret-ref>key</api-key-secret-ref>
                            <model>invalid-model-name</model>
                            <dimensions>1024</dimensions>
                        </component>
                    </container>
                </services>
                """;

        // Should fail because model name must start with 'voyage'
        Exception exception = assertThrows(IllegalArgumentException.class, () -> buildModelFromXml(servicesXml));
        assertTrue(exception.getMessage().contains("voyage"));
    }

    @Test
    void testVoyageAIEmbedderQuantizationSettings() throws Exception {
        // Test AUTO (default)
        String xmlAuto = """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version="1.0">
                    <container id="container" version="1.0">
                        <component id="voyage" type="voyage-ai-embedder">
                            <api-key-secret-ref>key</api-key-secret-ref>
                            <model>voyage-3</model>
                            <dimensions>1024</dimensions>
                        </component>
                    </container>
                </services>
                """;
        var modelAuto = buildModelFromXml(xmlAuto);
        var configAuto = getVoyageAIEmbedderConfig(modelAuto.getContainerClusters().get("container"), "voyage");
        assertEquals(VoyageAiEmbedderConfig.Quantization.Enum.AUTO, configAuto.quantization());

        // Test explicit quantization values
        String[] quantizations = {"float", "int8", "binary"};
        VoyageAiEmbedderConfig.Quantization.Enum[] expectedEnums = {
            VoyageAiEmbedderConfig.Quantization.Enum.FLOAT,
            VoyageAiEmbedderConfig.Quantization.Enum.INT8,
            VoyageAiEmbedderConfig.Quantization.Enum.BINARY
        };

        for (int i = 0; i < quantizations.length; i++) {
            String xml = String.format(java.util.Locale.ROOT, """
                    <?xml version="1.0" encoding="utf-8" ?>
                    <services version="1.0">
                        <container id="container" version="1.0">
                            <component id="voyage" type="voyage-ai-embedder">
                                <api-key-secret-ref>key</api-key-secret-ref>
                                <model>voyage-3</model>
                                <dimensions>1024</dimensions>
                                <quantization>%s</quantization>
                            </component>
                        </container>
                    </services>
                    """, quantizations[i]);

            var model = buildModelFromXml(xml);
            var config = getVoyageAIEmbedderConfig(model.getContainerClusters().get("container"), "voyage");
            assertEquals(expectedEnums[i], config.quantization());
        }
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

        assertNotEquals(fullConfig.apiKeySecretRef(), minimalConfig.apiKeySecretRef());
    }

    // ===== Helper Methods =====

    private VespaModel loadModel(Path path) throws Exception {
        return new VespaModelCreatorWithFilePkg(path.toFile()).create();
    }

    private VespaModel buildModelFromXml(String servicesXml) throws Exception {
        String hosts = "<hosts><host name='localhost'><alias>node1</alias></host></hosts>";
        return new VespaModelCreatorWithMockPkg(hosts, servicesXml).create();
    }

    private VoyageAiEmbedderConfig getVoyageAIEmbedderConfig(ApplicationContainerCluster cluster, String componentId) {
        Component<?, ?> component = cluster.getComponentsMap().get(new ComponentId(componentId));
        assertNotNull(component, "Component " + componentId + " should exist");
        assertInstanceOf(VoyageAIEmbedder.class, component, "Component should be VoyageAIEmbedder");

        VoyageAIEmbedder embedder = (VoyageAIEmbedder) component;
        VoyageAiEmbedderConfig.Builder builder = new VoyageAiEmbedderConfig.Builder();
        embedder.getConfig(builder);
        return builder.build();
    }
}
