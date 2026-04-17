// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.path.Path;
import com.yahoo.text.Text;
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
 *
 * @author bjorncs
 */
public class VoyageAIEmbedderTest {

    @Test
    void testVoyageAIEmbedderWithFullConfiguration() {
        var model = loadModel(Path.fromString("src/test/cfg/application/voyageai-embedder/"));
        var cluster = model.getContainerClusters().get("container");

        var component = cluster.getComponentsMap().get(new ComponentId("voyage-full"));
        assertNotNull(component, "VoyageAI embedder component should be present");
        assertInstanceOf(VoyageAIEmbedder.class, component);

        var config = getConfig(cluster, "voyage-full");
        assertEquals("voyage-3.5", config.model());
        assertEquals("voyage_api_key", config.apiKeySecretRef());
        assertEquals(1024, config.dimensions());
        assertEquals("https://api.voyageai.com/v1/embeddings", config.endpoint());
        assertTrue(config.truncate());
        assertEquals(16, config.batching().maxSize());
        assertEquals(200, config.batching().maxDelayMillis());
    }

    @Test
    void testVoyageAIEmbedderWithMinimalConfiguration() {
        var model = loadModel(Path.fromString("src/test/cfg/application/voyageai-embedder/"));
        var cluster = model.getContainerClusters().get("container");

        var config = getConfig(cluster, "voyage-minimal");
        assertEquals("voyage_key", config.apiKeySecretRef());
        assertEquals("voyage-3.5", config.model());
        assertEquals(1024, config.dimensions());
        assertEquals("https://api.voyageai.com/v1/embeddings", config.endpoint());
        assertTrue(config.truncate());
        assertEquals(0, config.batching().maxSize());
        assertEquals(0, config.batching().maxDelayMillis());
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

        var exception = assertThrows(IllegalArgumentException.class, () -> buildModelFromXml(servicesXml));
        assertTrue(exception.getMessage().contains("voyage"));
    }

    @Test
    void testVoyageAIEmbedderQuantizationSettings() {
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
        var configAuto = getConfig(modelAuto.getContainerClusters().get("container"), "voyage");
        assertEquals(VoyageAiEmbedderConfig.Quantization.Enum.AUTO, configAuto.quantization());

        String[] quantizations = {"float", "int8", "binary"};
        VoyageAiEmbedderConfig.Quantization.Enum[] expectedEnums = {
            VoyageAiEmbedderConfig.Quantization.Enum.FLOAT,
            VoyageAiEmbedderConfig.Quantization.Enum.INT8,
            VoyageAiEmbedderConfig.Quantization.Enum.BINARY
        };

        for (int i = 0; i < quantizations.length; i++) {
            String xml = Text.format("""
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

            var vespaModel = buildModelFromXml(xml);
            var config = getConfig(vespaModel.getContainerClusters().get("container"), "voyage");
            assertEquals(expectedEnums[i], config.quantization());
        }
    }

    @Test
    void testMultipleVoyageAIEmbedders() {
        var model = loadModel(Path.fromString("src/test/cfg/application/voyageai-embedder/"));
        var cluster = model.getContainerClusters().get("container");

        assertNotNull(cluster.getComponentsMap().get(new ComponentId("voyage-full")));
        assertNotNull(cluster.getComponentsMap().get(new ComponentId("voyage-minimal")));

        var fullConfig = getConfig(cluster, "voyage-full");
        var minimalConfig = getConfig(cluster, "voyage-minimal");
        assertNotEquals(fullConfig.apiKeySecretRef(), minimalConfig.apiKeySecretRef());
    }

    // ===== Helper Methods =====

    private VespaModel loadModel(Path path) {
        return new VespaModelCreatorWithFilePkg(path.toFile()).create();
    }

    private VespaModel buildModelFromXml(String servicesXml) {
        String hosts = "<hosts><host name='localhost'><alias>node1</alias></host></hosts>";
        return new VespaModelCreatorWithMockPkg(hosts, servicesXml).create();
    }

    private VoyageAiEmbedderConfig getConfig(ApplicationContainerCluster cluster, String componentId) {
        Component<?, ?> component = cluster.getComponentsMap().get(new ComponentId(componentId));
        assertNotNull(component, "Component " + componentId + " should exist");
        assertInstanceOf(VoyageAIEmbedder.class, component);
        var embedder = (VoyageAIEmbedder) component;
        var builder = new VoyageAiEmbedderConfig.Builder();
        embedder.getConfig(builder);
        return builder.build();
    }
}
