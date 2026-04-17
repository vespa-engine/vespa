// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import ai.vespa.embedding.config.OpenaiEmbedderConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.OpenAIEmbedder;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for OpenAI embedder configuration parsing and validation.
 *
 * @author bjorncs
 */
public class OpenAIEmbedderTest {

    @Test
    void testOpenAIEmbedderWithFullConfiguration() {
        var model = loadModel(Path.fromString("src/test/cfg/application/openai-embedder/"));
        var cluster = model.getContainerClusters().get("container");

        var component = cluster.getComponentsMap().get(new ComponentId("openai"));
        assertNotNull(component, "OpenAI embedder component should be present");
        assertInstanceOf(OpenAIEmbedder.class, component);

        var config = getConfig(cluster, "openai");
        assertEquals("text-embedding-3-small", config.model());
        assertEquals("openai_api_key", config.apiKeySecretRef());
        assertEquals(1024, config.dimensions());
        assertEquals("https://api.openai.com/v1/embeddings", config.endpoint());
    }

    @Test
    void testOpenAIEmbedderWithMinimalConfiguration() {
        var model = loadModel(Path.fromString("src/test/cfg/application/openai-embedder/"));
        var cluster = model.getContainerClusters().get("container");

        var config = getConfig(cluster, "openai-minimal");
        assertEquals("text-embedding-3-small", config.model());
        assertEquals(1536, config.dimensions());
        assertEquals("https://api.openai.com/v1/embeddings", config.endpoint());
    }

    // ===== Helper Methods =====

    private VespaModel loadModel(Path path) {
        return new VespaModelCreatorWithFilePkg(path.toFile()).create();
    }

    private OpenaiEmbedderConfig getConfig(ApplicationContainerCluster cluster, String componentId) {
        Component<?, ?> component = cluster.getComponentsMap().get(new ComponentId(componentId));
        assertNotNull(component, "Component " + componentId + " should exist");
        assertInstanceOf(OpenAIEmbedder.class, component);
        var embedder = (OpenAIEmbedder) component;
        var builder = new OpenaiEmbedderConfig.Builder();
        embedder.getConfig(builder);
        return builder.build();
    }
}
