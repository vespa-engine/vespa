// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import ai.vespa.embedding.config.MistralEmbedderConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.MistralEmbedder;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for Mistral embedder configuration parsing and validation.
 *
 * @author bjorncs
 */
public class MistralEmbedderTest {

    @Test
    void testMistralEmbedderConfiguration() {
        var model = loadModel(Path.fromString("src/test/cfg/application/mistral-embedder/"));
        var cluster = model.getContainerClusters().get("container");

        var component = cluster.getComponentsMap().get(new ComponentId("mistral"));
        assertNotNull(component, "Mistral embedder component should be present");
        assertInstanceOf(MistralEmbedder.class, component);

        var config = getConfig(cluster, "mistral");
        assertEquals("mistral-embed", config.model());
        assertEquals("mistral_key", config.apiKeySecretRef());
        assertEquals(1024, config.dimensions());
        assertEquals(MistralEmbedderConfig.Quantization.Enum.AUTO, config.quantization());
    }

    @Test
    void testQuantizationConfiguration() {
        var model = loadModel(Path.fromString("src/test/cfg/application/mistral-embedder/"));
        var cluster = model.getContainerClusters().get("container");

        var config = getConfig(cluster, "codestral");
        assertEquals("codestral-embed", config.model());
        assertEquals(2048, config.dimensions());
        assertEquals(MistralEmbedderConfig.Quantization.Enum.INT8, config.quantization());
    }

    private VespaModel loadModel(Path path) {
        return new VespaModelCreatorWithFilePkg(path.toFile()).create();
    }

    private MistralEmbedderConfig getConfig(ApplicationContainerCluster cluster, String componentId) {
        Component<?, ?> component = cluster.getComponentsMap().get(new ComponentId(componentId));
        assertNotNull(component, "Component " + componentId + " should exist");
        assertInstanceOf(MistralEmbedder.class, component);
        var embedder = (MistralEmbedder) component;
        var builder = new MistralEmbedderConfig.Builder();
        embedder.getConfig(builder);
        return builder.build();
    }
}
