// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.ModelReference;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import static com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig.PoolingStrategy;
import static com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig.TransformerExecutionMode;
import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;


/**
 * @author bjorncs
 */
public class HuggingFaceEmbedder extends TypedComponent implements HuggingFaceEmbedderConfig.Producer {

    private final OnnxModelOptions onnxModelOptions;

    public HuggingFaceEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super("ai.vespa.embedding.huggingface.HuggingFaceEmbedder", INTEGRATION_BUNDLE_NAME, xml);
        var model = Model.fromXml(state, xml, "transformer-model").orElseThrow();
        var modelRef = model.modelReference();
        this.onnxModelOptions = new OnnxModelOptions(
                modelRef,
                Model.fromXml(state, xml, "tokenizer-model")
                        .map(Model::modelReference)
                        .orElseGet(() -> resolveDefaultVocab(model, state)),
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
                getChildValue(xml, "pooling-strategy").orElse(null));
        model.registerOnnxModelCost(cluster);
    }

    private static ModelReference resolveDefaultVocab(Model model, DeployState state) {
        var modelId = model.modelId().orElse(null);
        if (state.isHosted() && modelId != null) {
            return Model.fromParams(state, model.name(), modelId + "-vocab", null, null).modelReference();
        }
        throw new IllegalArgumentException("'tokenizer-model' must be specified");
    }

    @Override
    public void getConfig(HuggingFaceEmbedderConfig.Builder b) {
        b.transformerModel(onnxModelOptions.modelRef()).tokenizerPath(onnxModelOptions.vocabRef());
        if (onnxModelOptions.maxTokens() != null) b.transformerMaxTokens(onnxModelOptions.maxTokens());
        if (onnxModelOptions.transformerInputIds() != null) b.transformerInputIds(onnxModelOptions.transformerInputIds());
        if (onnxModelOptions.transformerAttentionMask() != null) b.transformerAttentionMask(onnxModelOptions.transformerAttentionMask());
        if (onnxModelOptions.transformerTokenTypeIds() != null) b.transformerTokenTypeIds(onnxModelOptions.transformerTokenTypeIds());
        if (onnxModelOptions.transformerOutput() != null) b.transformerOutput(onnxModelOptions.transformerOutput());
        if (onnxModelOptions.normalize() != null) b.normalize(onnxModelOptions.normalize());
        if (onnxModelOptions.executionMode() != null) b.transformerExecutionMode(TransformerExecutionMode.Enum.valueOf(onnxModelOptions.executionMode()));
        if (onnxModelOptions.interOpThreads() != null) b.transformerInterOpThreads(onnxModelOptions.interOpThreads());
        if (onnxModelOptions.intraOpThreads() != null) b.transformerIntraOpThreads(onnxModelOptions.intraOpThreads());
        if (onnxModelOptions.gpuDevice() != null) b.transformerGpuDevice(onnxModelOptions.gpuDevice().deviceNumber());
        if (onnxModelOptions.poolingStrategy() != null) b.poolingStrategy(PoolingStrategy.Enum.valueOf(onnxModelOptions.poolingStrategy()));
    }

}
