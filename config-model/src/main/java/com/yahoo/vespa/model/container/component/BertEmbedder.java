// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.ModelReference;
import com.yahoo.config.model.api.OnnxModelOptions;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.embedding.BertBaseEmbedderConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import static com.yahoo.embedding.BertBaseEmbedderConfig.OnnxExecutionMode;
import static com.yahoo.embedding.BertBaseEmbedderConfig.PoolingStrategy;
import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;

/**
 * @author bjorncs
 */
public class BertEmbedder extends TypedComponent implements BertBaseEmbedderConfig.Producer {

    private final OnnxModelOptions onnxModelOptions;
    private final ModelReference modelRef;
    private final ModelReference vocabRef;
    private final Integer maxTokens;
    private final String transformerInputIds;
    private final String transformerAttentionMask;
    private final String transformerTokenTypeIds;
    private final String transformerOutput;
    private final Integer transformerStartSequenceToken;
    private final Integer transformerEndSequenceToken;
    private final String poolingStrategy;

    public BertEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super("ai.vespa.embedding.BertBaseEmbedder", INTEGRATION_BUNDLE_NAME, xml);
        var model = Model.fromXml(state, xml, "transformer-model").orElseThrow();
        this.onnxModelOptions = new OnnxModelOptions(
                getChildValue(xml, "onnx-execution-mode"),
                getChildValue(xml, "onnx-interop-threads").map(Integer::parseInt),
                getChildValue(xml, "onnx-intraop-threads").map(Integer::parseInt),
                getChildValue(xml, "onnx-gpu-device").map(Integer::parseInt).map(OnnxModelOptions.GpuDevice::new));
        modelRef = model.modelReference();
        vocabRef = Model.fromXml(state, xml, "tokenizer-vocab").orElseThrow().modelReference();
        maxTokens = getChildValue(xml, "max-tokens").map(Integer::parseInt).orElse(null);
        transformerInputIds = getChildValue(xml, "transformer-input-ids").orElse(null);
        transformerAttentionMask = getChildValue(xml, "transformer-attention-mask").orElse(null);
        transformerTokenTypeIds = getChildValue(xml, "transformer-token-type-ids").orElse(null);
        transformerOutput = getChildValue(xml, "transformer-output").orElse(null);
        transformerStartSequenceToken = getChildValue(xml, "transformer-start-sequence-token").map(Integer::parseInt).orElse(null);
        transformerEndSequenceToken = getChildValue(xml, "transformer-end-sequence-token").map(Integer::parseInt).orElse(null);
        poolingStrategy = getChildValue(xml, "pooling-strategy").orElse(null);
        model.registerOnnxModelCost(cluster, onnxModelOptions);
    }

    @Override
    public void getConfig(BertBaseEmbedderConfig.Builder b) {
        b.transformerModel(modelRef).tokenizerVocab(vocabRef);
        if (maxTokens != null) b.transformerMaxTokens(maxTokens);
        if (transformerInputIds != null) b.transformerInputIds(transformerInputIds);
        if (transformerAttentionMask != null) b.transformerAttentionMask(transformerAttentionMask);
        if (transformerTokenTypeIds != null) b.transformerTokenTypeIds(transformerTokenTypeIds);
        if (transformerOutput != null) b.transformerOutput(transformerOutput);
        if (transformerStartSequenceToken != null) b.transformerStartSequenceToken(transformerStartSequenceToken);
        if (transformerEndSequenceToken != null) b.transformerEndSequenceToken(transformerEndSequenceToken);
        if (poolingStrategy != null) b.poolingStrategy(PoolingStrategy.Enum.valueOf(poolingStrategy));
        onnxModelOptions.executionMode().ifPresent(value -> b.onnxExecutionMode(OnnxExecutionMode.Enum.valueOf(value)));
        onnxModelOptions.interOpThreads().ifPresent(b::onnxInterOpThreads);
        onnxModelOptions.intraOpThreads().ifPresent(b::onnxIntraOpThreads);
        onnxModelOptions.gpuDevice().ifPresent(value -> b.onnxGpuDevice(value.deviceNumber()));
    }

}
