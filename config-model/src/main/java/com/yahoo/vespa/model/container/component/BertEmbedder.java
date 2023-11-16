// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.embedding.BertBaseEmbedderConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import java.util.Optional;

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
                Optional.of(model.modelReference()),
                Optional.of(Model.fromXml(state, xml, "tokenizer-vocab").orElseThrow().modelReference()),
                getChildValue(xml, "max-tokens").map(Integer::parseInt),
                getChildValue(xml, "transformer-input-ids"),
                getChildValue(xml, "transformer-attention-mask"),
                getChildValue(xml, "transformer-token-type-ids"),
                getChildValue(xml, "transformer-output"),
                getChildValue(xml, "normalize").map(Boolean::parseBoolean),
                getChildValue(xml, "onnx-execution-mode"),
                getChildValue(xml, "onnx-interop-threads").map(Integer::parseInt),
                getChildValue(xml, "onnx-intraop-threads").map(Integer::parseInt),
                getChildValue(xml, "onnx-gpu-device").map(Integer::parseInt).map(OnnxModelOptions.GpuDevice::new),
                getChildValue(xml, "pooling-strategy"),
                getChildValue(xml, "transformer-start-sequence-token").map(Integer::parseInt),
                getChildValue(xml, "transformer-end-sequence-token").map(Integer::parseInt));
        model.registerOnnxModelCost(cluster);
    }

    @Override
    public void getConfig(BertBaseEmbedderConfig.Builder b) {
        b.transformerModel(onnxModelOptions.modelRef().get()).tokenizerVocab(onnxModelOptions.vocabRef().get());
        onnxModelOptions.maxTokens().ifPresent(b::transformerMaxTokens);
        onnxModelOptions.transformerInputIds().ifPresent(b::transformerInputIds);
        onnxModelOptions.transformerAttentionMask().ifPresent(b::transformerAttentionMask);
        onnxModelOptions.transformerTokenTypeIds().ifPresent(b::transformerTokenTypeIds);
        onnxModelOptions.transformerOutput().ifPresent(b::transformerOutput);
        onnxModelOptions.transformerStartSequenceToken().ifPresent(b::transformerStartSequenceToken);
        onnxModelOptions.transformerEndSequenceToken().ifPresent(b::transformerEndSequenceToken);
        onnxModelOptions.poolingStrategy().ifPresent(value -> b.poolingStrategy(PoolingStrategy.Enum.valueOf(value)));
        onnxModelOptions.executionMode().ifPresent(value -> b.onnxExecutionMode(OnnxExecutionMode.Enum.valueOf(value)));
        onnxModelOptions.interOpThreads().ifPresent(b::onnxInterOpThreads);
        onnxModelOptions.intraOpThreads().ifPresent(b::onnxIntraOpThreads);
        onnxModelOptions.gpuDevice().ifPresent(value -> b.onnxGpuDevice(value.deviceNumber()));
    }

}
