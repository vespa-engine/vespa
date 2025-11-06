// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.InnerNode;
import com.yahoo.config.ModelNode;
import com.yahoo.config.ModelReference;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.embedding.BertBaseEmbedderConfig;
import com.yahoo.embedding.ColBertEmbedderConfig;
import com.yahoo.embedding.SpladeEmbedderConfig;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.language.huggingface.config.HuggingFaceTokenizerConfig;
import com.yahoo.onnx.OnnxEvaluatorConfig;
import com.yahoo.path.Path;
import com.yahoo.text.XML;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.component.BertEmbedder;
import com.yahoo.vespa.model.container.component.ColBertEmbedder;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.HuggingFaceEmbedder;
import com.yahoo.vespa.model.container.component.HuggingFaceTokenizer;
import com.yahoo.vespa.model.container.component.SpladeEmbedder;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author bjorncs
 * @author glebashnik
 */
public class EmbedderTestCase {

    @Test
    void testApplicationComponentWithModelReference_hosted() throws IOException, SAXException {
        String input = "<component id='test' class='ai.vespa.example.paragraph.ApplicationSpecificEmbedder' bundle='app'>" +
                       "  <config name='ai.vespa.example.paragraph.sentence-embedder'>" +
                       "    <model model-id='minilm-l6-v2' />" +
                       "    <vocab model-id='bert-base-uncased' />" +
                       "  </config>" +
                       "</component>";
        String component = "<component id='test' class='ai.vespa.example.paragraph.ApplicationSpecificEmbedder' bundle='app'>" +
                           "  <config name='ai.vespa.example.paragraph.sentence-embedder'>" +
                           "      <model  model-id='minilm-l6-v2' url='https://data.vespa-cloud.com/onnx_models/sentence_all_MiniLM_L6_v2.onnx' />" +
                           "      <vocab model-id='bert-base-uncased' url='https://data.vespa-cloud.com/onnx_models/bert-base-uncased-vocab.txt' />" +
                           "  </config>" +
                           "</component>";
        assertTransform(input, component, true);
    }

    @Test
    void testUnknownModelId_hosted() throws IOException, SAXException {
        String embedder = "<component id='test' class='ai.vespa.example.paragraph.ApplicationSpecificEmbedder'>" +
                          "  <config name='ai.vespa.example.paragraph.sentence-embedder'>" +
                          "    <model model-id='my_model_id' />" +
                          "    <vocab model-id='my_vocab_id' />" +
                          "  </config>" +
                          "</component>";
        assertTransformThrows(embedder,
                              "Unknown model id 'my_model_id' on 'model'",
                              true);
    }
    
    @Test
    void huggingfaceEmbedder_selfhosted() throws Exception {
        var model = loadModel(Path.fromString("src/test/cfg/application/embed/"), false);
        var cluster = model.getContainerClusters().get("container");
        
        var embedder = assertHuggingfaceEmbedderComponentPresent(cluster);
        var embedderCfgBuilder = new HuggingFaceEmbedderConfig.Builder();
        embedder.getConfig(embedderCfgBuilder);
        var embedderCfg = embedderCfgBuilder.build();
        
        assertEquals("my_input_ids", embedderCfg.transformerInputIds());
        assertEquals("https://my/url/model.onnx", modelReference(embedderCfg, "transformerModel").url().orElseThrow().value());
        assertEquals(1024, embedderCfg.transformerMaxTokens());
        
        var tokenizer = assertHuggingfaceTokenizerComponentPresent(cluster);
        var tokenizerCfgBuilder = new HuggingFaceTokenizerConfig.Builder();
        tokenizer.getConfig(tokenizerCfgBuilder);
        var tokenizerCfg = tokenizerCfgBuilder.build();
        
        assertEquals("https://my/url/tokenizer.json", modelReference(tokenizerCfg.model().get(0), "path").url().orElseThrow().value());
        assertEquals(-1, tokenizerCfg.maxLength());
        assertEquals("Represent this sentence for searching relevant passages:", embedderCfg.prependQuery());
        assertEquals("passage:", embedderCfg.prependDocument());
        
        var onnxCfgBuilder = new OnnxEvaluatorConfig.Builder();
        embedder.getConfig(onnxCfgBuilder);
        var onnxCfg = onnxCfgBuilder.build();
        
        assertEquals(OnnxEvaluatorConfig.ExecutionMode.Enum.parallel, onnxCfg.executionMode());
        assertEquals(10, onnxCfg.intraOpThreads());
        assertEquals(8, onnxCfg.interOpThreads());
        assertEquals(1, onnxCfg.gpuDevice());
        assertEquals(16, onnxCfg.batching().maxSize());
        assertEquals(100, onnxCfg.batching().maxDelayMillis());
        assertEquals(OnnxEvaluatorConfig.Concurrency.FactorType.Enum.relative, onnxCfg.concurrency().factorType());
        assertEquals(2.0, onnxCfg.concurrency().factor(), 0.001);
        assertTrue(onnxCfg.modelConfigOverride().isPresent());
        assertEquals("files/hf_config.pbtxt", onnxCfg.modelConfigOverride().get().toString());
    }

    @Test
    void huggingfaceEmbedder_hosted() throws Exception {
        var model = loadModel(Path.fromString("src/test/cfg/application/embed/"), true);
        var cluster = model.getContainerClusters().get("container");

        var embedder = assertHuggingfaceEmbedderComponentPresent(cluster);
        var embedderCfgBuilder = new HuggingFaceEmbedderConfig.Builder();
        embedder.getConfig(embedderCfgBuilder);
        var embedderCfg = embedderCfgBuilder.build();

        assertEquals("my_input_ids", embedderCfg.transformerInputIds());
        assertEquals("https://data.vespa-cloud.com/onnx_models/e5-base-v2/model.onnx", modelReference(embedderCfg, "transformerModel").url().orElseThrow().value());
        assertEquals(1024, embedderCfg.transformerMaxTokens());

        var tokenizer = assertHuggingfaceTokenizerComponentPresent(cluster);
        var tokenizerCfgBuilder = new HuggingFaceTokenizerConfig.Builder();
        tokenizer.getConfig(tokenizerCfgBuilder);
        var tokenizerCfg = tokenizerCfgBuilder.build();

        assertEquals("https://data.vespa-cloud.com/onnx_models/multilingual-e5-base/tokenizer.json", modelReference(tokenizerCfg.model().get(0), "path").url().orElseThrow().value());
        assertEquals(-1, tokenizerCfg.maxLength());
        assertEquals("Represent this sentence for searching relevant passages:", embedderCfg.prependQuery());
        assertEquals("passage:", embedderCfg.prependDocument());
    }

    @Test
    void spladeEmbedder_selfhosted() throws Exception {
        var model = loadModel(Path.fromString("src/test/cfg/application/embed/"), false);
        var cluster = model.getContainerClusters().get("container");

        var embedder = assertSpladeEmbedderComponentPresent(cluster);
        var embedderCfgBuilder = new SpladeEmbedderConfig.Builder();
        embedder.getConfig(embedderCfgBuilder);
        var embedderCfg = embedderCfgBuilder.build();

        assertEquals("my_input_ids", embedderCfg.transformerInputIds());
        assertEquals("https://my/url/model.onnx", modelReference(embedderCfg, "transformerModel").url().orElseThrow().value());
        assertEquals(0.2, embedderCfg.termScoreThreshold());
        assertEquals(1024, embedderCfg.transformerMaxTokens());

        var tokenizer = assertHuggingfaceTokenizerComponentPresent(cluster);
        var tokenizerCfgBuilder = new HuggingFaceTokenizerConfig.Builder();
        tokenizer.getConfig(tokenizerCfgBuilder);
        var tokenizerCfg = tokenizerCfgBuilder.build();

        assertEquals("https://my/url/tokenizer.json", modelReference(tokenizerCfg.model().get(0), "path").url().orElseThrow().value());
        assertEquals(-1, tokenizerCfg.maxLength());

        var onnxCfgBuilder = new OnnxEvaluatorConfig.Builder();
        embedder.getConfig(onnxCfgBuilder);
        var onnxCfg = onnxCfgBuilder.build();

        assertEquals(OnnxEvaluatorConfig.ExecutionMode.Enum.parallel, onnxCfg.executionMode());
        assertEquals(10, onnxCfg.intraOpThreads());
        assertEquals(8, onnxCfg.interOpThreads());
        assertEquals(1, onnxCfg.gpuDevice());
        assertEquals(8, onnxCfg.batching().maxSize());
        assertEquals(50, onnxCfg.batching().maxDelayMillis());
        assertEquals(OnnxEvaluatorConfig.Concurrency.FactorType.Enum.absolute, onnxCfg.concurrency().factorType());
        assertEquals(4.0, onnxCfg.concurrency().factor(), 0.001);
        assertTrue(onnxCfg.modelConfigOverride().isPresent());
        assertEquals("files/splade_config.pbtxt", onnxCfg.modelConfigOverride().get().toString());
    }

    @Test
    void colBertEmbedder_selfhosted() throws Exception {
        var model = loadModel(Path.fromString("src/test/cfg/application/embed/"), false);
        var cluster = model.getContainerClusters().get("container");

        var embedder = assertColBertEmbedderComponentPresent(cluster);
        var embedderCfgBuilder = new ColBertEmbedderConfig.Builder();
        embedder.getConfig(embedderCfgBuilder);
        var embedderCfg = embedderCfgBuilder.build();

        assertEquals("my_input_ids", embedderCfg.transformerInputIds());
        assertEquals("https://my/url/model.onnx", modelReference(embedderCfg, "transformerModel").url().orElseThrow().value());
        assertEquals(1024, embedderCfg.transformerMaxTokens());

        var tokenizer = assertHuggingfaceTokenizerComponentPresent(cluster);
        var tokenizerCfgBuilder = new HuggingFaceTokenizerConfig.Builder();
        tokenizer.getConfig(tokenizerCfgBuilder);
        var tokenizerCfg = tokenizerCfgBuilder.build();

        assertEquals("https://my/url/tokenizer.json", modelReference(tokenizerCfg.model().get(0), "path").url().orElseThrow().value());
        assertEquals(-1, tokenizerCfg.maxLength());
        assertEquals(1, embedderCfg.queryTokenId());
        assertEquals(2, embedderCfg.documentTokenId());
        assertEquals(0, embedderCfg.transformerPadToken());
        assertEquals(103, embedderCfg.transformerMaskToken());

        var onnxCfgBuilder = new OnnxEvaluatorConfig.Builder();
        embedder.getConfig(onnxCfgBuilder);
        var onnxCfg = onnxCfgBuilder.build();

        assertEquals(OnnxEvaluatorConfig.ExecutionMode.Enum.parallel, onnxCfg.executionMode());
        assertEquals(10, onnxCfg.intraOpThreads());
        assertEquals(8, onnxCfg.interOpThreads());
        assertEquals(1, onnxCfg.gpuDevice());
        assertEquals(12, onnxCfg.batching().maxSize());
        assertEquals(150, onnxCfg.batching().maxDelayMillis());
        assertEquals(OnnxEvaluatorConfig.Concurrency.FactorType.Enum.relative, onnxCfg.concurrency().factorType());
        assertEquals(1.5, onnxCfg.concurrency().factor(), 0.001);
        assertTrue(onnxCfg.modelConfigOverride().isPresent());
        assertEquals("files/colbert_config.pbtxt", onnxCfg.modelConfigOverride().get().toString());
    }

    @Test
    void colBertEmbedder_hosted() throws Exception {
        var model = loadModel(Path.fromString("src/test/cfg/application/embed/"), true);
        var cluster = model.getContainerClusters().get("container");

        var embedder = assertColBertEmbedderComponentPresent(cluster);
        var embedderCfgBuilder = new ColBertEmbedderConfig.Builder();
        embedder.getConfig(embedderCfgBuilder);
        var embedderCfg = embedderCfgBuilder.build();

        assertEquals("my_input_ids", embedderCfg.transformerInputIds());
        assertEquals("https://data.vespa-cloud.com/onnx_models/e5-base-v2/model.onnx", modelReference(embedderCfg, "transformerModel").url().orElseThrow().value());
        assertEquals(1024, embedderCfg.transformerMaxTokens());

        var tokenizer = assertHuggingfaceTokenizerComponentPresent(cluster);
        var tokenizerCfgBuilder = new HuggingFaceTokenizerConfig.Builder();
        tokenizer.getConfig(tokenizerCfgBuilder);
        var tokenizerCfg = tokenizerCfgBuilder.build();

        assertEquals("https://data.vespa-cloud.com/onnx_models/multilingual-e5-base/tokenizer.json", modelReference(tokenizerCfg.model().get(0), "path").url().orElseThrow().value());
        assertEquals(-1, tokenizerCfg.maxLength());
        assertEquals(1, embedderCfg.queryTokenId());
        assertEquals(2, embedderCfg.documentTokenId());
        assertEquals(0, embedderCfg.transformerPadToken());
        assertEquals(103, embedderCfg.transformerMaskToken());
    }

    @Test
    void bertEmbedder_selfhosted() throws Exception {
        var model = loadModel(Path.fromString("src/test/cfg/application/embed/"), false);
        var cluster = model.getContainerClusters().get("container");

        var embedder = assertBertEmbedderComponentPresent(cluster);
        var embedderCfgBuilder = new BertBaseEmbedderConfig.Builder();
        embedder.getConfig(embedderCfgBuilder);
        var embedderCfg = embedderCfgBuilder.build();

        assertEquals("https://my/url/model.onnx", modelReference(embedderCfg, "transformerModel").url().orElseThrow().value());
        assertEquals("files/vocab.txt", modelReference(embedderCfg, "tokenizerVocab").path().orElseThrow().value());
        assertEquals("", embedderCfg.transformerTokenTypeIds());

        var onnxCfgBuilder = new OnnxEvaluatorConfig.Builder();
        embedder.getConfig(onnxCfgBuilder);
        var onnxCfg = onnxCfgBuilder.build();

        assertEquals(OnnxEvaluatorConfig.ExecutionMode.Enum.parallel, onnxCfg.executionMode());
        assertEquals(4, onnxCfg.intraOpThreads());
        assertEquals(8, onnxCfg.interOpThreads());
        assertEquals(1, onnxCfg.gpuDevice());
        assertEquals(32, onnxCfg.batching().maxSize());
        assertEquals(200, onnxCfg.batching().maxDelayMillis());
        assertEquals(OnnxEvaluatorConfig.Concurrency.FactorType.Enum.absolute, onnxCfg.concurrency().factorType());
        assertEquals(2.0, onnxCfg.concurrency().factor(), 0.001);
        assertTrue(onnxCfg.modelConfigOverride().isPresent());
        assertEquals("files/bert_config.pbtxt", onnxCfg.modelConfigOverride().get().toString());
    }

    @Test
    void bertEmbedder_hosted() throws Exception {
        var model = loadModel(Path.fromString("src/test/cfg/application/embed/"), true);
        var cluster = model.getContainerClusters().get("container");

        var embedder = assertBertEmbedderComponentPresent(cluster);
        var embedderCfgBuilder = new BertBaseEmbedderConfig.Builder();
        embedder.getConfig(embedderCfgBuilder);
        var embedderCfg = embedderCfgBuilder.build();

        assertEquals("https://data.vespa-cloud.com/onnx_models/sentence_all_MiniLM_L6_v2.onnx",
                     modelReference(embedderCfg, "transformerModel").url().orElseThrow().value());
        assertTrue(modelReference(embedderCfg, "tokenizerVocab").url().isEmpty());
        assertEquals("files/vocab.txt", modelReference(embedderCfg, "tokenizerVocab").path().orElseThrow().value());
        assertEquals("", embedderCfg.transformerTokenTypeIds());
    }

    @Test
    void passesXmlValidation() {
        new VespaModelCreatorWithFilePkg("src/test/cfg/application/embed/").create();
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
        assertEquals("minilm-l6-v2 https://data.vespa-cloud.com/onnx_models/sentence_all_MiniLM_L6_v2.onnx \"\"",
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
            assertEquals("model is configured with only a 'model-id'. Add a 'path' or 'url' to deploy this outside Vespa Cloud",
                         Exceptions.toMessageString(e));
        }
    }

    private VespaModel loadModel(Path path, boolean hosted) throws Exception {
        FilesApplicationPackage applicationPackage = FilesApplicationPackage.fromDir(path.toFile(), Map.of());
        TestProperties properties = new TestProperties().setHostedVespa(hosted);
        DeployState state = new DeployState.Builder()
                .properties(properties)
                .endpoints(hosted ? Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("default.example.com"))) : Set.of())
                .applicationPackage(applicationPackage)
                .build();
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

    private static HuggingFaceTokenizer assertHuggingfaceTokenizerComponentPresent(ApplicationContainerCluster cluster) {
        var hfTokenizer = (HuggingFaceTokenizer) cluster.getComponentsMap().get(new ComponentId("hf-tokenizer"));
        assertEquals("com.yahoo.language.huggingface.HuggingFaceTokenizer", hfTokenizer.getClassId().getName());
        return hfTokenizer;
    }

    private static HuggingFaceEmbedder assertHuggingfaceEmbedderComponentPresent(ApplicationContainerCluster cluster) {
        var hfEmbedder = (HuggingFaceEmbedder) cluster.getComponentsMap().get(new ComponentId("hf-embedder"));
        assertEquals("ai.vespa.embedding.HuggingFaceEmbedder", hfEmbedder.getClassId().getName());
        return hfEmbedder;
    }

    private static ColBertEmbedder assertColBertEmbedderComponentPresent(ApplicationContainerCluster cluster) {
        var colbert = (ColBertEmbedder) cluster.getComponentsMap().get(new ComponentId("colbert"));
        assertEquals("ai.vespa.embedding.ColBertEmbedder", colbert.getClassId().getName());
        return colbert;
    }

    private static SpladeEmbedder assertSpladeEmbedderComponentPresent(ApplicationContainerCluster cluster) {
        var splade = (SpladeEmbedder) cluster.getComponentsMap().get(new ComponentId("splade"));
        assertEquals("ai.vespa.embedding.SpladeEmbedder", splade.getClassId().getName());
        return splade;
    }

    private static BertEmbedder assertBertEmbedderComponentPresent(ApplicationContainerCluster cluster) {
        var bertEmbedder = (BertEmbedder) cluster.getComponentsMap().get(new ComponentId("bert-embedder"));
        assertEquals("ai.vespa.embedding.BertBaseEmbedder", bertEmbedder.getClassId().getName());
        return bertEmbedder;
    }

    // Ugly hack to read underlying model reference from config instance
    private static ModelReference modelReference(InnerNode cfg, String name) {
        try {
            var f = cfg.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return ((ModelNode) f.get(cfg)).getModelReference();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
