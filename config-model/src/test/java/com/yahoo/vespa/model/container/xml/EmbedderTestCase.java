// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.path.Path;
import com.yahoo.text.XML;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class EmbedderTestCase {

    private static final String BUNDLED_EMBEDDER_CLASS = "ai.vespa.embedding.BertBaseEmbedder";
    private static final String BUNDLED_EMBEDDER_CONFIG = "embedding.bert-base-embedder";

    @Test
    void testBundledEmbedder_selfhosted() throws IOException, SAXException {
        String input = "<component id='test' class='" + BUNDLED_EMBEDDER_CLASS + "' bundle='model-integration'>" +
                       "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                       "    <transformerModel id='my_model_id' url='my-model-url' />" +
                       "    <tokenizerVocab id='my_vocab_id' url='my-vocab-url' />" +
                       "  </config>" +
                       "</component>";
        String component = "<component id='test' class='" + BUNDLED_EMBEDDER_CLASS + "' bundle='model-integration'>" +
                           "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                           "    <transformerModel id='my_model_id' url='my-model-url' />" +
                           "    <tokenizerVocab id='my_vocab_id' url='my-vocab-url' />" +
                           "  </config>" +
                           "</component>";
        assertTransform(input, component, false);
    }

    @Test
    void testBundledEmbedder_hosted() throws IOException, SAXException {
        String input = "<component id='test' class='" + BUNDLED_EMBEDDER_CLASS + "' bundle='model-integration'>" +
                       "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                       "    <transformerModel model-id='minilm-l6-v2' />" +
                       "    <tokenizerVocab model-id='bert-base-uncased' path='ignored.txt'/>" +
                       "  </config>" +
                       "</component>";
        String component = "<component id='test' class='" + BUNDLED_EMBEDDER_CLASS + "' bundle='model-integration'>" +
                           "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                           "      <transformerModel model-id='minilm-l6-v2' url='https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx' />" +
                           "      <tokenizerVocab model-id='bert-base-uncased' url='https://data.vespa.oath.cloud/onnx_models/bert-base-uncased-vocab.txt' />" +
                           "  </config>" +
                           "</component>";
        assertTransform(input, component, true);
    }

    @Test
    void testApplicationComponentWithModelReference_hosted() throws IOException, SAXException {
        String input = "<component id='test' class='ApplicationSpecificEmbedder' bundle='model-integration'>" +
                       "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                       "    <transformerModel model-id='minilm-l6-v2' />" +
                       "    <tokenizerVocab model-id='bert-base-uncased' />" +
                       "  </config>" +
                       "</component>";
        String component = "<component id='test' class='ApplicationSpecificEmbedder' bundle='model-integration'>" +
                           "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                           "      <transformerModel  model-id='minilm-l6-v2' url='https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx' />" +
                           "      <tokenizerVocab model-id='bert-base-uncased' url='https://data.vespa.oath.cloud/onnx_models/bert-base-uncased-vocab.txt' />" +
                           "  </config>" +
                           "</component>";
        assertTransform(input, component, true);
    }

    @Test
    void testUnknownModelId_hosted() throws IOException, SAXException {
        String embedder = "<component id='test' class='" + BUNDLED_EMBEDDER_CLASS + "'>" +
                          "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                          "    <transformerModel model-id='my_model_id' />" +
                          "    <tokenizerVocab model-id='my_vocab_id' />" +
                          "  </config>" +
                          "</component>";
        assertTransformThrows(embedder,
                              "Unknown model id 'my_model_id' on 'transformerModel'",
                              true);
    }

    @Test
    void testApplicationPackageWithEmbedder_selfhosted() throws Exception  {
        Path applicationDir = Path.fromString("src/test/cfg/application/embed/");
        VespaModel model = loadModel(applicationDir, false);
        ApplicationContainerCluster containerCluster = model.getContainerClusters().get("container");

        Component<?, ?> transformer = containerCluster.getComponentsMap().get(new ComponentId("transformer"));
        ConfigPayloadBuilder config = transformer.getUserConfigs().get(new ConfigDefinitionKey("bert-base-embedder", "embedding"));
        assertEquals("minilm-l6-v2 application-url \"\"", config.getObject("transformerModel").getValue());
        assertEquals("\"\" \"\" files/vocab.txt", config.getObject("tokenizerVocab").getValue());
        assertEquals("4", config.getObject("onnxIntraOpThreads").getValue());
    }

    @Test
    void testApplicationPackageWithEmbedder_hosted() throws Exception  {
        Path applicationDir = Path.fromString("src/test/cfg/application/embed/");
        VespaModel model = loadModel(applicationDir, true);
        ApplicationContainerCluster containerCluster = model.getContainerClusters().get("container");

        Component<?, ?> transformer = containerCluster.getComponentsMap().get(new ComponentId("transformer"));
        ConfigPayloadBuilder config = transformer.getUserConfigs().get(new ConfigDefinitionKey("bert-base-embedder", "embedding"));
        assertEquals("minilm-l6-v2 https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx \"\"",
                     config.getObject("transformerModel").getValue());
        assertEquals("\"\" \"\" files/vocab.txt", config.getObject("tokenizerVocab").getValue());
        assertEquals("4", config.getObject("onnxIntraOpThreads").getValue());
    }

    @Test
    void testApplicationPackageWithApplicationEmbedder_selfhosted() throws Exception  {
        Path applicationDir = Path.fromString("src/test/cfg/application/embed_generic/");
        VespaModel model = loadModel(applicationDir, false);
        ApplicationContainerCluster containerCluster = model.getContainerClusters().get("container");

        Component<?, ?> testComponent = containerCluster.getComponentsMap().get(new ComponentId("transformer"));
        ConfigPayloadBuilder config = testComponent.getUserConfigs().get(new ConfigDefinitionKey("sentence-embedder", "ai.vespa.example.paragraph"));
        assertEquals("minilm-l6-v2 application-url \"\"", config.getObject("model").getValue());
        assertEquals("\"\" \"\" files/vocab.txt", config.getObject("vocab").getValue());
    }

    @Test
    void testApplicationPackageWithApplicationEmbedder_hosted() throws Exception  {
        Path applicationDir = Path.fromString("src/test/cfg/application/embed_generic/");
        VespaModel model = loadModel(applicationDir, true);
        ApplicationContainerCluster containerCluster = model.getContainerClusters().get("container");

        Component<?, ?> testComponent = containerCluster.getComponentsMap().get(new ComponentId("transformer"));
        ConfigPayloadBuilder config = testComponent.getUserConfigs().get(new ConfigDefinitionKey("sentence-embedder", "ai.vespa.example.paragraph"));
        assertEquals("minilm-l6-v2 https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx \"\"",
                     config.getObject("model").getValue());
        assertEquals("\"\" \"\" files/vocab.txt", config.getObject("vocab").getValue());
    }

    @Test
    void testApplicationPackageWithApplicationEmbedder_selfhosted_cloud_only() throws Exception  {
        try {
            Path applicationDir = Path.fromString("src/test/cfg/application/embed_cloud_only/");
            VespaModel model = loadModel(applicationDir, false);
            fail("Expected failure");
        }
        catch (IllegalArgumentException e) {
            assertEquals("transformerModel is configured with only a 'model-id'. Add a 'path' or 'url' to deploy this outside Vespa Cloud",
                         Exceptions.toMessageString(e));
        }
    }

    private VespaModel loadModel(Path path, boolean hosted) throws Exception {
        FilesApplicationPackage applicationPackage = FilesApplicationPackage.fromFile(path.toFile());
        TestProperties properties = new TestProperties().setHostedVespa(hosted);
        DeployState state = new DeployState.Builder().properties(properties).applicationPackage(applicationPackage).build();
        return new VespaModel(state);
    }

    private void assertTransform(String inputComponent, String expectedComponent, boolean hosted) throws IOException, SAXException {
        Element component = createElement(inputComponent);
        ModelIdResolver.resolveModelIds(component, hosted);
        assertSpec(createElement(expectedComponent), component);
    }

    private void assertSpec(Element e1, Element e2) {
        assertEquals(e1.getTagName(), e2.getTagName());
        assertAttributes(e1, e2);
        assertAttributes(e2, e1);
        assertEquals(XML.getValue(e1).trim(), XML.getValue(e2).trim(), "Content of " + e1.getTagName() + "' is identical");
        assertChildren(e1, e2);
    }

    private void assertAttributes(Element e1, Element e2) {
        NamedNodeMap map = e1.getAttributes();
        for (int i = 0; i < map.getLength(); ++i) {
            String attribute = map.item(i).getNodeName();
            assertEquals(e1.getAttribute(attribute), e2.getAttribute(attribute),
                         "Attribute '" + attribute + "' is equal");
        }
    }

    private void assertChildren(Element e1, Element e2) {
        List<Element> list1 = XML.getChildren(e1);
        List<Element> list2 = XML.getChildren(e2);
        assertEquals(list1.size(), list2.size());
        for (int i = 0; i < list1.size(); ++i) {
            Element child1 = list1.get(i);
            Element child2 = list2.get(i);
            assertSpec(child1, child2);
        }
    }

    private void assertTransformThrows(String embedder, String expectedMessage, boolean hosted) throws IOException, SAXException {
        try {
            ModelIdResolver.resolveModelIds(createElement(embedder), hosted);
            fail("Expected exception was not thrown: " + expectedMessage);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(expectedMessage), "Expected error message not found");
        }
    }

    private Element createElement(String xml) throws IOException, SAXException {
        Document doc = XML.getDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return (Element) doc.getFirstChild();
    }

}
