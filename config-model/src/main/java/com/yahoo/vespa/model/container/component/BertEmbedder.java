// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

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

    public BertEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super("ai.vespa.embedding.BertBaseEmbedder", INTEGRATION_BUNDLE_NAME, xml);
        var model = Model.fromXml(state, xml, "transformer-model").orElseThrow();
        this.onnxModelOptions = new OnnxModelOptions(
                model.modelReference(),
                Model.fromXml(state, xml, "tokenizer-vocab").orElseThrow().modelReference(),
                getChildValue(xml, "max-tokens").map(Integer::parseInt).orElse(null),
                getChildValue(xml, "transformer-input-ids").orElse(null),
                getChildValue(xml, "transformer-attention-mask").orElse(null),
                getChildValue(xml, "transformer-token-type-ids").orElse(null),
                getChildValue(xml, "transformer-output").orElse(null),
                getChildValue(xml, "normalize").map(Boolean::parseBoolean).orElse(null),
                getChildValue(xml, "onnx-execution-mode").orElse(null),
                getChildValue(xml, "onnx-interop-threads").map(Integer::parseInt).orElse(null),
                getChildValue(xml, "onnx-intraop-threads").map(Integer::parseInt).orElse(null),
                getChildValue(xml, "onnx-gpu-device").map(Integer::parseInt).map(OnnxModelOptions.GpuDevice::new).orElse(null),
                getChildValue(xml, "pooling-strategy").orElse(null),
                getChildValue(xml, "transformer-start-sequence-token").map(Integer::parseInt).orElse(null),
                getChildValue(xml, "transformer-end-sequence-token").map(Integer::parseInt).orElse(null));
        model.registerOnnxModelCost(cluster);
    }

    @Override
    public void getConfig(BertBaseEmbedderConfig.Builder b) {
        b.transformerModel(onnxModelOptions.modelRef()).tokenizerVocab(onnxModelOptions.vocabRef());
        if (onnxModelOptions.maxTokens() != null) b.transformerMaxTokens(onnxModelOptions.maxTokens());
        if (onnxModelOptions.transformerInputIds() != null) b.transformerInputIds(onnxModelOptions.transformerInputIds());
        if (onnxModelOptions.transformerAttentionMask() != null) b.transformerAttentionMask(onnxModelOptions.transformerAttentionMask());
        if (onnxModelOptions.transformerTokenTypeIds() != null) b.transformerTokenTypeIds(onnxModelOptions.transformerTokenTypeIds());
        if (onnxModelOptions.transformerOutput() != null) b.transformerOutput(onnxModelOptions.transformerOutput());
        if (onnxModelOptions.transformerStartSequenceToken() != null) b.transformerStartSequenceToken(onnxModelOptions.transformerStartSequenceToken());
        if (onnxModelOptions.transformerEndSequenceToken() != null) b.transformerEndSequenceToken(onnxModelOptions.transformerEndSequenceToken());
        if (onnxModelOptions.poolingStrategy() != null) b.poolingStrategy(PoolingStrategy.Enum.valueOf(onnxModelOptions.poolingStrategy()));
        if (onnxModelOptions.executionMode() != null) b.onnxExecutionMode(OnnxExecutionMode.Enum.valueOf(onnxModelOptions.executionMode()));
        if (onnxModelOptions.interOpThreads() != null) b.onnxInterOpThreads(onnxModelOptions.interOpThreads());
        if (onnxModelOptions.intraOpThreads() != null) b.onnxIntraOpThreads(onnxModelOptions.intraOpThreads());
        if (onnxModelOptions.gpuDevice() != null) b.onnxGpuDevice(onnxModelOptions.gpuDevice().deviceNumber());
    }

}
