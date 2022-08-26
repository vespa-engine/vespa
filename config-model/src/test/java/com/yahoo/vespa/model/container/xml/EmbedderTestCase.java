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

    private static final String emptyPathFileName = "services.xml";
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
                           "      <transformerModelUrl>my-model-url</transformerModelUrl>" +
                           "      <transformerModelPath>services.xml</transformerModelPath>" +
                           "      <tokenizerVocabUrl>my-vocab-url</tokenizerVocabUrl>" +
                           "      <tokenizerVocabPath>services.xml</tokenizerVocabPath>" +
                           "  </config>" +
                           "</component>";
        assertTransform(input, component, false);
    }

    @Test
    void testPathHasPriority_selfhosted() throws IOException, SAXException {
        String input = "<component id='test' class='" + BUNDLED_EMBEDDER_CLASS + "' bundle='model-integration'>" +
                       "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                       "    <transformerModel id='my_model_id' url='my-model-url' path='files/model.onnx' />" +
                       "    <tokenizerVocab id='my_vocab_id' url='my-vocab-url' path='files/vocab.txt' />" +
                       "  </config>" +
                       "</component>";
        String component = "<component id='test' class='" + BUNDLED_EMBEDDER_CLASS + "' bundle='model-integration'>" +
                           "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                           "      <transformerModelUrl></transformerModelUrl>" +
                           "      <transformerModelPath>files/model.onnx</transformerModelPath>" +
                           "      <tokenizerVocabUrl></tokenizerVocabUrl>" +
                           "      <tokenizerVocabPath>files/vocab.txt</tokenizerVocabPath>" +
                           "  </config>" +
                           "</component>";
        assertTransform(input, component, false);
    }

    @Test
    void testBundledEmbedder_hosted() throws IOException, SAXException {
        String input = "<component id='test' class='" + BUNDLED_EMBEDDER_CLASS + "' bundle='model-integration'>" +
                       "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                       "    <transformerModel id='minilm-l6-v2' />" +
                       "    <tokenizerVocab id='bert-base-uncased' />" +
                       "  </config>" +
                       "</component>";
        String component = "<component id='test' class='" + BUNDLED_EMBEDDER_CLASS + "' bundle='model-integration'>" +
                           "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                           "      <transformerModelUrl>https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx</transformerModelUrl>" +
                           "      <transformerModelPath>services.xml</transformerModelPath>" +
                           "      <tokenizerVocabUrl>https://data.vespa.oath.cloud/onnx_models/bert-base-uncased-vocab.txt</tokenizerVocabUrl>" +
                           "      <tokenizerVocabPath>services.xml</tokenizerVocabPath>" +
                           "  </config>" +
                           "</component>";
        assertTransform(input, component, true);
    }

    @Test
    void testApplicationEmbedderWithBundledConfig_hosted() throws IOException, SAXException {
        String input = "<component id='test' class='ApplicationSpecificEmbedder' bundle='model-integration'>" +
                       "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                       "    <transformerModel id='minilm-l6-v2' />" +
                       "    <tokenizerVocab id='bert-base-uncased' />" +
                       "  </config>" +
                       "</component>";
        String component = "<component id='test' class='ApplicationSpecificEmbedder' bundle='model-integration'>" +
                           "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                           "      <transformerModelUrl>https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx</transformerModelUrl>" +
                           "      <transformerModelPath>services.xml</transformerModelPath>" +
                           "      <tokenizerVocabUrl>https://data.vespa.oath.cloud/onnx_models/bert-base-uncased-vocab.txt</tokenizerVocabUrl>" +
                           "      <tokenizerVocabPath>services.xml</tokenizerVocabPath>" +
                           "  </config>" +
                           "</component>";
        assertTransform(input, component, true);
    }

    @Test
    void testUnknownModelId_hosted() throws IOException, SAXException {
        String embedder = "<component id='test' class='" + BUNDLED_EMBEDDER_CLASS + "'>" +
                          "  <config name='" + BUNDLED_EMBEDDER_CONFIG + "'>" +
                          "    <transformerModel id='my_model_id' />" +
                          "    <tokenizerVocab id='my_vocab_id' />" +
                          "  </config>" +
                          "</component>";
        assertTransformThrows(embedder,
                              "Unknown embedder model 'my_model_id'. " +
                              "Available models are [bert-base-uncased, minilm-l6-v2]",
                              true);
    }

    @Test
    void testApplicationPackageWithEmbedder_selfhosted() throws Exception  {
        Path applicationDir = Path.fromString("src/test/cfg/application/embed/");
        VespaModel model = loadModel(applicationDir, false);
        ApplicationContainerCluster containerCluster = model.getContainerClusters().get("container");

        Component<?, ?> transformer = containerCluster.getComponentsMap().get(new ComponentId("transformer"));
        ConfigPayloadBuilder config = transformer.getUserConfigs().get(new ConfigDefinitionKey("bert-base-embedder", "embedding"));
        assertEquals("application-url", config.getObject("transformerModelUrl").getValue());
        assertEquals(emptyPathFileName, config.getObject("transformerModelPath").getValue());
        assertEquals("", config.getObject("tokenizerVocabUrl").getValue());
        assertEquals("files/vocab.txt", config.getObject("tokenizerVocabPath").getValue());
        assertEquals("4", config.getObject("onnxIntraOpThreads").getValue());
    }

    @Test
    void testApplicationPackageWithEmbedder_hosted() throws Exception  {
        Path applicationDir = Path.fromString("src/test/cfg/application/embed/");
        VespaModel model = loadModel(applicationDir, true);
        ApplicationContainerCluster containerCluster = model.getContainerClusters().get("container");

        Component<?, ?> transformer = containerCluster.getComponentsMap().get(new ComponentId("transformer"));
        ConfigPayloadBuilder config = transformer.getUserConfigs().get(new ConfigDefinitionKey("bert-base-embedder", "embedding"));
        assertEquals("https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx",
                     config.getObject("transformerModelUrl").getValue());
        assertEquals(emptyPathFileName, config.getObject("transformerModelPath").getValue());
        assertEquals("", config.getObject("tokenizerVocabUrl").getValue());
        assertEquals("files/vocab.txt", config.getObject("tokenizerVocabPath").getValue());
        assertEquals("4", config.getObject("onnxIntraOpThreads").getValue());
    }

    @Test
    void testApplicationPackageWithApplicationEmbedder_selfhosted() throws Exception  {
        Path applicationDir = Path.fromString("src/test/cfg/application/embed_generic/");
        VespaModel model = loadModel(applicationDir, false);
        ApplicationContainerCluster containerCluster = model.getContainerClusters().get("container");

        Component<?, ?> testComponent = containerCluster.getComponentsMap().get(new ComponentId("transformer"));
        ConfigPayloadBuilder config = testComponent.getUserConfigs().get(new ConfigDefinitionKey("sentence-embedder", "ai.vespa.example.paragraph"));
        assertEquals("application-url", config.getObject("modelUrl").getValue());
        assertEquals(emptyPathFileName, config.getObject("modelPath").getValue());
        assertEquals("files/vocab.txt", config.getObject("vocabPath").getValue());
        assertEquals("foo", config.getObject("myValue").getValue());
    }

    @Test
    void testApplicationPackageWithApplicationEmbedder_hosted() throws Exception  {
        Path applicationDir = Path.fromString("src/test/cfg/application/embed_generic/");
        VespaModel model = loadModel(applicationDir, true);
        ApplicationContainerCluster containerCluster = model.getContainerClusters().get("container");

        Component<?, ?> testComponent = containerCluster.getComponentsMap().get(new ComponentId("transformer"));
        ConfigPayloadBuilder config = testComponent.getUserConfigs().get(new ConfigDefinitionKey("sentence-embedder", "ai.vespa.example.paragraph"));
        assertEquals("https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx",
                     config.getObject("modelUrl").getValue());
        assertEquals(emptyPathFileName, config.getObject("modelPath").getValue());
        assertEquals("files/vocab.txt", config.getObject("vocabPath").getValue());
        assertEquals("foo", config.getObject("myValue").getValue());
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

    private void assertTransform(String embedder, String expectedComponent, boolean hosted) throws IOException, SAXException {
        assertSpec(createElement(expectedComponent),
                   ModelConfigTransformer.transform(createEmptyDeployState(hosted), createElement(embedder)));
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
            ModelConfigTransformer.transform(createEmptyDeployState(hosted), createElement(embedder));
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
