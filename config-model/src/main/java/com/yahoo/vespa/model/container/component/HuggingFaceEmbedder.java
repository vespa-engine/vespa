// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.ModelReference;
import com.yahoo.config.model.api.OnnxModelOptions;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import java.util.Set;

import static com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig.PoolingStrategy;
import static com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig.TransformerExecutionMode;
import static com.yahoo.text.XML.getChild;
import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;
import static com.yahoo.vespa.model.container.xml.ModelIdResolver.HF_TOKENIZER;
import static com.yahoo.vespa.model.container.xml.ModelIdResolver.ONNX_MODEL;


/**
 * @author bjorncs
 */
public class HuggingFaceEmbedder extends TypedComponent implements HuggingFaceEmbedderConfig.Producer {

    private final OnnxModelOptions onnxModelOptions;
    private final ModelReference modelRef;
    private final ModelReference vocabRef;
    private final Integer maxTokens;
    private final String transformerInputIds;
    private final String transformerAttentionMask;
    private final String transformerTokenTypeIds;
    private final String transformerOutput;
    private final Boolean normalize;
    private final String poolingStrategy;

    private String prependQuery;

    private String prependDocument;

    public HuggingFaceEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super("ai.vespa.embedding.huggingface.HuggingFaceEmbedder", INTEGRATION_BUNDLE_NAME, xml);
        var model = Model.fromXml(state, xml, "transformer-model", Set.of(ONNX_MODEL)).orElseThrow();
        this.onnxModelOptions = new OnnxModelOptions(
                getChildValue(xml, "onnx-execution-mode"),
                getChildValue(xml, "onnx-interop-threads").map(Integer::parseInt),
                getChildValue(xml, "onnx-intraop-threads").map(Integer::parseInt),
                getChildValue(xml, "onnx-gpu-device").map(Integer::parseInt).map(OnnxModelOptions.GpuDevice::new));
        modelRef = model.modelReference();
        vocabRef = Model.fromXmlOrImplicitlyFromOnnxModel(state, xml, model, "tokenizer-model", Set.of(HF_TOKENIZER)).modelReference();
        maxTokens = getChildValue(xml, "max-tokens").map(Integer::parseInt).orElse(null);
        transformerInputIds = getChildValue(xml, "transformer-input-ids").orElse(null);
        transformerAttentionMask = getChildValue(xml, "transformer-attention-mask").orElse(null);
        transformerTokenTypeIds = getChildValue(xml, "transformer-token-type-ids").orElse(null);
        transformerOutput = getChildValue(xml, "transformer-output").orElse(null);
        normalize = getChildValue(xml, "normalize").map(Boolean::parseBoolean).orElse(null);
        poolingStrategy = getChildValue(xml, "pooling-strategy").orElse(null);
        Element prepend = getChild(xml, "prepend");
        if (prepend != null) {
            prependQuery = getChildValue(prepend, "query").orElse(null);
            prependDocument = getChildValue(prepend, "document").orElse(null);
        }

        model.registerOnnxModelCost(cluster, onnxModelOptions);
    }

    @Override
    public void getConfig(HuggingFaceEmbedderConfig.Builder b) {
        b.transformerModel(modelRef).tokenizerPath(vocabRef);
        if (maxTokens != null) b.transformerMaxTokens(maxTokens);
        if (transformerInputIds != null) b.transformerInputIds(transformerInputIds);
        if (transformerAttentionMask != null) b.transformerAttentionMask(transformerAttentionMask);
        if (transformerTokenTypeIds != null) b.transformerTokenTypeIds(transformerTokenTypeIds);
        if (transformerOutput != null) b.transformerOutput(transformerOutput);
        if (normalize != null) b.normalize(normalize);
        if (poolingStrategy != null) b.poolingStrategy(PoolingStrategy.Enum.valueOf(poolingStrategy));
        if(prependQuery != null) b.prependQuery(prependQuery);
        if(prependDocument != null) b.prependDocument(prependDocument);
        onnxModelOptions.executionMode().ifPresent(value -> b.transformerExecutionMode(TransformerExecutionMode.Enum.valueOf(value)));
        onnxModelOptions.interOpThreads().ifPresent(b::transformerInterOpThreads);
        onnxModelOptions.intraOpThreads().ifPresent(b::transformerIntraOpThreads);
        onnxModelOptions.gpuDevice().ifPresent(value -> b.transformerGpuDevice(value.deviceNumber()));
    }

}
