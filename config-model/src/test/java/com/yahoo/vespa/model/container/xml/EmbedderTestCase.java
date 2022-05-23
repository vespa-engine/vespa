// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.producer.UserConfigRepo;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.text.XML;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.xml.embedder.EmbedderConfig;
import com.yahoo.vespa.model.container.xml.embedder.EmbedderOption;
import com.yahoo.vespa.model.ml.ImportedModelTester;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EmbedderTestCase {

    private static final String PREDEFINED_EMBEDDER_CLASS = "ai.vespa.embedding.BertBaseEmbedder";
    private static final String PREDEFINED_EMBEDDER_CONFIG = "embedding.bert-base-embedder";

    @Test
    public void testGenericEmbedConfig() throws IOException, SAXException {
        String embedder = "<embedder id=\"test\" class=\"ai.vespa.test\" bundle=\"bundle\" def=\"def.name\">" +
                "  <val>123</val>" +
                "</embedder>";
        String component = "<component id=\"test\" class=\"ai.vespa.test\" bundle=\"bundle\">" +
                "  <config name=\"def.name\">" +
                "    <val>123</val>" +
                "  </config>" +
                "</component>";
        assertTransform(embedder, component);
    }

    @Test
    public void testGenericEmbedConfigRequiresBundleAndDef() throws IOException, SAXException {
        assertTransformThrows("<embedder id=\"test\" class=\"ai.vespa.test\"></embedder>",
                "Embedder configuration requires a bundle name");
        assertTransformThrows("<embedder id=\"test\" class=\"ai.vespa.test\" bundle=\"bundle\"></embedder>",
                "Embedder configuration requires a config definition name");
    }

    @Test
    public void testPredefinedEmbedConfigSelfHosted() throws IOException, SAXException {
        assertTransformThrows("<embedder id=\"test\" class=\"" + PREDEFINED_EMBEDDER_CLASS + "\"></embedder>",
                "Embedder '" + PREDEFINED_EMBEDDER_CLASS + "' requires options for [vocab, model]");
        assertTransformThrows("<embedder id=\"test\" class=\"" + PREDEFINED_EMBEDDER_CLASS + "\">" +
                "  <model />" +
                "  <vocab />" +
                "</embedder>",
                "Model option requires either a 'path' or a 'url' attribute");
        assertTransformThrows("<embedder id=\"test\" class=\"" + PREDEFINED_EMBEDDER_CLASS + "\">" +
                "  <model id=\"my_model_id\" />" +
                "  <vocab id=\"my_vocab_id\" />" +
                "</embedder>",
                "Model option 'id' is not valid here");

        String embedder = "<embedder id=\"test\" class=\"" + PREDEFINED_EMBEDDER_CLASS + "\">" +
                "  <model id=\"my_model_id\" url=\"my-model-url\" />" +
                "  <vocab id=\"my_vocab_id\" url=\"my-vocab-url\" />" +
                "</embedder>";
        String component = "<component id=\"test\" class=\"" + PREDEFINED_EMBEDDER_CLASS + "\" bundle=\"model-integration\">" +
                "  <config name=\"" + PREDEFINED_EMBEDDER_CONFIG + "\">" +
                "      <tokenizerVocabUrl>my-vocab-url</tokenizerVocabUrl>" +
                "      <tokenizerVocabPath></tokenizerVocabPath>" +
                "      <transformerModelUrl>my-model-url</transformerModelUrl>" +
                "      <transformerModelPath></transformerModelPath>" +
                "  </config>" +
                "</component>";
        assertTransform(embedder, component, false);

        // Path has priority:
        embedder = "<embedder id=\"test\" class=\"" + PREDEFINED_EMBEDDER_CLASS + "\">" +
                "  <model id=\"my_model_id\" url=\"my-model-url\" path=\"files/model.onnx\" />" +
                "  <vocab id=\"my_vocab_id\" url=\"my-vocab-url\" path=\"files/vocab.txt\" />" +
                "</embedder>";
        component = "<component id=\"test\" class=\"" + PREDEFINED_EMBEDDER_CLASS + "\" bundle=\"model-integration\">" +
                "  <config name=\"" + PREDEFINED_EMBEDDER_CONFIG + "\">" +
                "      <tokenizerVocabPath>files/vocab.txt</tokenizerVocabPath>" +
                "      <tokenizerVocabUrl></tokenizerVocabUrl>" +
                "      <transformerModelPath>files/model.onnx</transformerModelPath>" +
                "      <transformerModelUrl></transformerModelUrl>" +
                "  </config>" +
                "</component>";
        assertTransform(embedder, component, false);
    }

    @Test
    public void testPredefinedEmbedConfigCloud() throws IOException, SAXException {
        String embedder = "<embedder id=\"test\" class=\"" + PREDEFINED_EMBEDDER_CLASS + "\" />";
        String component = "<component id=\"test\" class=\"" + PREDEFINED_EMBEDDER_CLASS + "\" bundle=\"model-integration\">" +
                "  <config name=\"" + PREDEFINED_EMBEDDER_CONFIG + "\">" +
                "      <tokenizerVocabUrl>some url</tokenizerVocabUrl>" +
                "      <tokenizerVocabPath></tokenizerVocabPath>" +
                "      <transformerModelUrl>some url</transformerModelUrl>" +
                "      <transformerModelPath></transformerModelPath>" +
                "  </config>" +
                "</component>";
        assertTransform(embedder, component, true);

        embedder = "<embedder id=\"test\" class=\"" + PREDEFINED_EMBEDDER_CLASS + "\">" +
                "  <model id=\"my_model_id\" />" +
                "  <vocab id=\"my_vocab_id\" />" +
                "</embedder>";
        assertTransformThrows(embedder, "Unknown model id: 'my_vocab_id'", true);

        embedder = "<embedder id=\"test\" class=\"" + PREDEFINED_EMBEDDER_CLASS + "\">" +
                "  <model id=\"test-model-id\" />" +
                "  <vocab id=\"test-model-id\" />" +
                "</embedder>";
        component = "<component id=\"test\" class=\"" + PREDEFINED_EMBEDDER_CLASS + "\" bundle=\"model-integration\">" +
                "  <config name=\"" + PREDEFINED_EMBEDDER_CONFIG + "\">" +
                "      <tokenizerVocabUrl>test-model-url</tokenizerVocabUrl>" +
                "      <tokenizerVocabPath></tokenizerVocabPath>" +
                "      <transformerModelUrl>test-model-url</transformerModelUrl>" +
                "      <transformerModelPath></transformerModelPath>" +
                "  </config>" +
                "</component>";
        assertTransform(embedder, component, true);
    }

    @Test
    public void testEmbedConfig() throws Exception  {
        final String emptyPathFileName = "services.xml";

        Path applicationDir = Path.fromString("src/test/cfg/application/embed/");
        VespaModel model = loadModel(applicationDir, false);
        ApplicationContainerCluster containerCluster = model.getContainerClusters().get("container");

        Component<?, ?> testComponent = containerCluster.getComponentsMap().get(new ComponentId("test"));
        ConfigPayloadBuilder testConfig = testComponent.getUserConfigs().get(new ConfigDefinitionKey("dummy", "test"));
        assertEquals(testConfig.getObject("num").getValue(), "12");
        assertEquals(testConfig.getObject("str").getValue(), "some text");

        Component<?, ?> transformer = containerCluster.getComponentsMap().get(new ComponentId("transformer"));
        ConfigPayloadBuilder transformerConfig = transformer.getUserConfigs().get(new ConfigDefinitionKey("bert-base-embedder", "embedding"));
        assertEquals(transformerConfig.getObject("transformerModelUrl").getValue(), "test-model-url");
        assertEquals(transformerConfig.getObject("transformerModelPath").getValue(), emptyPathFileName);
        assertEquals(transformerConfig.getObject("tokenizerVocabUrl").getValue(), "");
        assertEquals(transformerConfig.getObject("tokenizerVocabPath").getValue(), "files/vocab.txt");
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

    private void assertTransformThrows(String embedder, String msg) throws IOException, SAXException {
        assertTransformThrows(embedder, msg, false);
    }

    private void assertTransformThrows(String embedder, String msg, boolean hosted) throws IOException, SAXException {
        try {
            EmbedderConfig.transform(createEmptyDeployState(hosted), createElement(embedder));
            fail("Expected exception was not thrown: " + msg);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), msg);
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
