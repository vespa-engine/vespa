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
import com.yahoo.vespa.model.container.xml.embedder.EmbedderConfig;
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
import static org.junit.jupiter.api.Assertions.fail;

public class EmbedderTestCase {

    private static final String PREDEFINED_EMBEDDER_CLASS = "ai.vespa.embedding.BertBaseEmbedder";
    private static final String PREDEFINED_EMBEDDER_CONFIG = "embedding.bert-base-embedder";

    @Test
    void testGenericEmbedConfig() throws IOException, SAXException {
        String embedder = "<embedder id='test' class='ai.vespa.test' bundle='bundle' def='def.name'>" +
                          "  <val>123</val>" +
                          "</embedder>";
        String component = "<component id='test' class='ai.vespa.test' bundle='bundle'>" +
                           "  <config name='def.name'>" +
                           "    <val>123</val>" +
                           "  </config>" +
                           "</component>";
        assertTransform(embedder, component);
    }

    @Test
    void testPredefinedEmbedConfigSelfHosted() throws IOException, SAXException {
        String embedder = "<embedder id='test' class='" + PREDEFINED_EMBEDDER_CLASS + "'>" +
                          "  <transformerModel id='my_model_id' url='my-model-url' />" +
                          "  <tokenizerVocab id='my_vocab_id' url='my-vocab-url' />" +
                          "</embedder>";
        String component = "<component id='test' class='" + PREDEFINED_EMBEDDER_CLASS + "' bundle='model-integration'>" +
                           "  <config name='" + PREDEFINED_EMBEDDER_CONFIG + "'>" +
                           "      <transformerModelUrl>my-model-url</transformerModelUrl>" +
                           "      <transformerModelPath></transformerModelPath>" +
                           "      <tokenizerVocabUrl>my-vocab-url</tokenizerVocabUrl>" +
                           "      <tokenizerVocabPath></tokenizerVocabPath>" +
                           "  </config>" +
                           "</component>";
        assertTransform(embedder, component, false);
    }

    @Test
    void testPathHasPrioritySelfHosted() throws IOException, SAXException {
        String embedder = "<embedder id='test' class='" + PREDEFINED_EMBEDDER_CLASS + "'>" +
                          "  <transformerModel id='my_model_id' url='my-model-url' path='files/model.onnx' />" +
                          "  <tokenizerVocab id='my_vocab_id' url='my-vocab-url' path='files/vocab.txt' />" +
                          "</embedder>";
        String component = "<component id='test' class='" + PREDEFINED_EMBEDDER_CLASS + "' bundle='model-integration'>" +
                           "  <config name='" + PREDEFINED_EMBEDDER_CONFIG + "'>" +
                           "      <transformerModelUrl></transformerModelUrl>" +
                           "      <transformerModelPath>files/model.onnx</transformerModelPath>" +
                           "      <tokenizerVocabUrl></tokenizerVocabUrl>" +
                           "      <tokenizerVocabPath>files/vocab.txt</tokenizerVocabPath>" +
                           "  </config>" +
                           "</component>";
        assertTransform(embedder, component, false);
    }

    @Test
    void testPredefinedEmbedConfigCloud() throws IOException, SAXException {
        String embedder = "<embedder id='test' class='" + PREDEFINED_EMBEDDER_CLASS + "'>" +
                          "  <transformerModel id='test-model-id' />" +
                          "  <tokenizerVocab id='test-model-id' />" +
                          "</embedder>";
        String component = "<component id='test' class='" + PREDEFINED_EMBEDDER_CLASS + "' bundle='model-integration'>" +
                           "  <config name='" + PREDEFINED_EMBEDDER_CONFIG + "'>" +
                           "      <transformerModelUrl>test-model-url</transformerModelUrl>" +
                           "      <transformerModelPath></transformerModelPath>" +
                           "      <tokenizerVocabUrl>test-model-url</tokenizerVocabUrl>" +
                           "      <tokenizerVocabPath></tokenizerVocabPath>" +
                           "  </config>" +
                           "</component>";
        assertTransform(embedder, component, true);
    }

    @Test
    void testCustomEmbedderWithPredefinedConfigCloud() throws IOException, SAXException {
        String embedder = "<embedder id='test' class='ApplicationSpecificEmbedder' def='" + PREDEFINED_EMBEDDER_CONFIG + "'>" +
                          "  <transformerModel id='test-model-id' />" +
                          "  <tokenizerVocab id='test-model-id' />" +
                          "</embedder>";
        String component = "<component id='test' class='ApplicationSpecificEmbedder' bundle='model-integration'>" +
                           "  <config name='" + PREDEFINED_EMBEDDER_CONFIG + "'>" +
                           "      <transformerModelUrl>test-model-url</transformerModelUrl>" +
                           "      <transformerModelPath></transformerModelPath>" +
                           "      <tokenizerVocabUrl>test-model-url</tokenizerVocabUrl>" +
                           "      <tokenizerVocabPath></tokenizerVocabPath>" +
                           "  </config>" +
                           "</component>";
        assertTransform(embedder, component, true);
    }

    @Test
    void testUnknownModelIdCloud() throws IOException, SAXException {
        String embedder = "<embedder id='test' class='" + PREDEFINED_EMBEDDER_CLASS + "'>" +
                          "  <transformerModel id='my_model_id' />" +
                          "  <tokenizerVocab id='my_vocab_id' />" +
                          "</embedder>";
        assertTransformThrows(embedder, "Unknown model id 'my_model_id'", true);
    }

    @Test
    void testApplicationWithEmbedConfig() throws Exception  {
        final String emptyPathFileName = "services.xml";

        Path applicationDir = Path.fromString("src/test/cfg/application/embed/");
        VespaModel model = loadModel(applicationDir, false);
        ApplicationContainerCluster containerCluster = model.getContainerClusters().get("container");

        Component<?, ?> testComponent = containerCluster.getComponentsMap().get(new ComponentId("test"));
        ConfigPayloadBuilder testConfig = testComponent.getUserConfigs().get(new ConfigDefinitionKey("dummy", "test"));
        assertEquals("12", testConfig.getObject("num").getValue());
        assertEquals("some text", testConfig.getObject("str").getValue());

        Component<?, ?> transformer = containerCluster.getComponentsMap().get(new ComponentId("transformer"));
        ConfigPayloadBuilder transformerConfig = transformer.getUserConfigs().get(new ConfigDefinitionKey("bert-base-embedder", "embedding"));
        assertEquals("test-model-url", transformerConfig.getObject("transformerModelUrl").getValue());
        assertEquals(emptyPathFileName, transformerConfig.getObject("transformerModelPath").getValue());
        assertEquals("", transformerConfig.getObject("tokenizerVocabUrl").getValue());
        assertEquals("files/vocab.txt", transformerConfig.getObject("tokenizerVocabPath").getValue());
    }

    @Test
    void testApplicationWithGenericEmbedConfig() throws Exception  {
        Path applicationDir = Path.fromString("src/test/cfg/application/embed_generic/");
        VespaModel model = loadModel(applicationDir, false);
        ApplicationContainerCluster containerCluster = model.getContainerClusters().get("container");

        Component<?, ?> testComponent = containerCluster.getComponentsMap().get(new ComponentId("transformer"));
        ConfigPayloadBuilder config = testComponent.getUserConfigs().get(new ConfigDefinitionKey("sentence-embedder", "ai.vespa.example.paragraph"));
        assertEquals("files/vocab.txt", config.getObject("vocab").getValue());
        assertEquals("files/model.onnx", config.getObject("modelPath").getValue());
    }

    private VespaModel loadModel(Path path, boolean hosted) throws Exception {
        FilesApplicationPackage applicationPackage = FilesApplicationPackage.fromFile(path.toFile());
        TestProperties properties = new TestProperties().setHostedVespa(hosted);
        DeployState state = new DeployState.Builder().properties(properties).applicationPackage(applicationPackage).build();
        return new VespaModel(state);
    }

    private void assertTransform(String embedder, String component) throws IOException, SAXException {
        assertTransform(embedder, component, false);
    }

    private void assertTransform(String embedder, String component, boolean hosted) throws IOException, SAXException {
        Element emb = createElement(embedder);
        Element cmp = createElement(component);
        Element trans = EmbedderConfig.transform(createEmptyDeployState(hosted), emb);
        assertSpec(cmp, trans);
    }

    private void assertSpec(Element e1, Element e2) {
        assertEquals(e1.getTagName(), e2.getTagName());
        assertAttributes(e1, e2);
        assertAttributes(e2, e1);
        assertChildren(e1, e2);
    }

    private void assertAttributes(Element e1, Element e2) {
        NamedNodeMap map = e1.getAttributes();
        for (int i = 0; i < map.getLength(); ++i) {
            String attr = map.item(i).getNodeName();
            assertEquals(e1.getAttribute(attr), e2.getAttribute(attr));
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
            EmbedderConfig.transform(createEmptyDeployState(hosted), createElement(embedder));
            fail("Expected exception was not thrown: " + expectedMessage);
        } catch (IllegalArgumentException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    private Element createElement(String xml) throws IOException, SAXException {
        Document doc = XML.getDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return (Element) doc.getFirstChild();
    }

    private DeployState createEmptyDeployState(boolean hosted) {
        TestProperties properties = new TestProperties().setHostedVespa(hosted);
        return new DeployState.Builder().properties(properties).build();
    }

}
