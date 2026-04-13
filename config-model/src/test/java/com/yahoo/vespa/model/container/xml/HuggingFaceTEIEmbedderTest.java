// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import ai.vespa.embedding.config.HuggingFaceTeiEmbedderConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.path.Path;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.HuggingFaceTEIEmbedder;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HuggingFaceTEIEmbedderTest {

    @Test
    void testHuggingFaceTEIEmbedderWithFullConfiguration() throws Exception {
        VespaModel model = loadModel(Path.fromString("src/test/cfg/application/huggingface-tei-embedder/"));
        ApplicationContainerCluster cluster = model.getContainerClusters().get("container");

        Component<?, ?> component = cluster.getComponentsMap().get(new ComponentId("hf-tei-full"));
        assertNotNull(component, "HuggingFace TEI embedder component should be present");
        assertInstanceOf(HuggingFaceTEIEmbedder.class, component, "Component should be HuggingFaceTEIEmbedder");

        HuggingFaceTeiEmbedderConfig config = getEmbedderConfig(cluster, "hf-tei-full");

        assertEquals("http://tei.local:8080/embed", config.endpoint());
        assertEquals("hf_tei_api_key", config.apiKeySecretRef());
        assertEquals(768, config.dimensions());
        assertTrue(config.normalize());
        assertTrue(config.truncate());
        assertEquals(HuggingFaceTeiEmbedderConfig.TruncationDirection.Enum.LEFT, config.truncationDirection());
        assertEquals("default_prompt", config.promptName());
        assertEquals("query_prompt", config.queryPromptName());
        assertEquals("document_prompt", config.documentPromptName());
        assertEquals(5, config.maxRetries());
        assertEquals(45000, config.timeout());
        assertEquals(32, config.batching().maxSize());
        assertEquals(50, config.batching().maxDelayMillis());
    }

    @Test
    void testHuggingFaceTEIEmbedderWithMinimalConfiguration() throws Exception {
        VespaModel model = loadModel(Path.fromString("src/test/cfg/application/huggingface-tei-embedder/"));
        ApplicationContainerCluster cluster = model.getContainerClusters().get("container");

        HuggingFaceTeiEmbedderConfig config = getEmbedderConfig(cluster, "hf-tei-minimal");

        assertEquals("http://localhost:8080/embed", config.endpoint());
        assertEquals("", config.apiKeySecretRef());
        assertEquals(0, config.dimensions());
        assertTrue(config.normalize());
        assertEquals(false, config.truncate());
        assertEquals(HuggingFaceTeiEmbedderConfig.TruncationDirection.Enum.RIGHT, config.truncationDirection());
        assertEquals("", config.promptName());
        assertEquals("", config.queryPromptName());
        assertEquals("", config.documentPromptName());
        assertEquals(3, config.maxRetries());
        assertEquals(60000, config.timeout());
        assertEquals(0, config.batching().maxSize());
        assertEquals(0, config.batching().maxDelayMillis());
    }

    @Test
    void testInvalidTruncationDirectionFailsValidation() {
        String servicesXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version="1.0">
                    <container id="container" version="1.0">
                        <component id="hf-tei" type="hugging-face-tei-embedder">
                            <truncation-direction>middle</truncation-direction>
                        </component>
                    </container>
                </services>
                """;

        Exception exception = assertThrows(IllegalArgumentException.class, () -> buildModelFromXml(servicesXml));
        assertTrue(exception.getMessage().contains("truncation-direction"));
    }

    @Test
    void testNegativeDimensionsFailsValidation() {
        String servicesXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <services version="1.0">
                    <container id="container" version="1.0">
                        <component id="hf-tei" type="hugging-face-tei-embedder">
                            <dimensions>-1</dimensions>
                        </component>
                    </container>
                </services>
                """;

        Exception exception = assertThrows(IllegalArgumentException.class, () -> buildModelFromXml(servicesXml));
        assertTrue(exception.getMessage().contains("dimensions"));
    }

    private VespaModel loadModel(Path path) throws Exception {
        return new VespaModelCreatorWithFilePkg(path.toFile()).create();
    }

    private VespaModel buildModelFromXml(String servicesXml) throws Exception {
        String hosts = "<hosts><host name='localhost'><alias>node1</alias></host></hosts>";
        return new VespaModelCreatorWithMockPkg(hosts, servicesXml).create();
    }

    private HuggingFaceTeiEmbedderConfig getEmbedderConfig(ApplicationContainerCluster cluster, String componentId) {
        Component<?, ?> component = cluster.getComponentsMap().get(new ComponentId(componentId));
        assertNotNull(component, "Component " + componentId + " should exist");
        assertInstanceOf(HuggingFaceTEIEmbedder.class, component, "Component should be HuggingFaceTEIEmbedder");

        HuggingFaceTEIEmbedder embedder = (HuggingFaceTEIEmbedder) component;
        HuggingFaceTeiEmbedderConfig.Builder builder = new HuggingFaceTeiEmbedderConfig.Builder();
        embedder.getConfig(builder);
        return builder.build();
    }
}
