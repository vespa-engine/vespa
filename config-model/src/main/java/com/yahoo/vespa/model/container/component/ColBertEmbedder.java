// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.ModelReference;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.embedding.ColBertEmbedderConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import java.util.Optional;

import static com.yahoo.embedding.ColBertEmbedderConfig.TransformerExecutionMode;
import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;


/**
 * @author bergum
 */
public class ColBertEmbedder extends TypedComponent implements ColBertEmbedderConfig.Producer {

    private final OnnxModelOptions onnxModelOptions;

    public ColBertEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super("ai.vespa.embedding.ColBertEmbedder", INTEGRATION_BUNDLE_NAME, xml);
        var model = Model.fromXml(state, xml, "transformer-model").orElseThrow();
        this.onnxModelOptions = new OnnxModelOptions(
                Optional.of(model.modelReference()),
                Optional.of(Model.fromXml(state, xml, "tokenizer-model")
                        .map(Model::modelReference)
                        .orElseGet(() -> resolveDefaultVocab(model, state))),
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
                getChildValue(xml, "transformer-end-sequence-token").map(Integer::parseInt),
                getChildValue(xml, "max-query-tokens").map(Integer::parseInt),
                getChildValue(xml, "max-document-tokens").map(Integer::parseInt),
                getChildValue(xml, "transformer-mask-token").map(Integer::parseInt));
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
    public void getConfig(ColBertEmbedderConfig.Builder b) {
        b.transformerModel(onnxModelOptions.modelRef().get()).tokenizerPath(onnxModelOptions.vocabRef().get());
       onnxModelOptions.maxTokens().ifPresent(b::transformerMaxTokens);
       onnxModelOptions.transformerInputIds().ifPresent(b::transformerInputIds);
       onnxModelOptions.transformerAttentionMask().ifPresent(b::transformerAttentionMask);
       onnxModelOptions.transformerOutput().ifPresent(b::transformerOutput);
       onnxModelOptions.maxQueryTokens().ifPresent(b::maxQueryTokens);
       onnxModelOptions.maxDocumentTokens().ifPresent(b::maxDocumentTokens);
       onnxModelOptions.transformerStartSequenceToken().ifPresent(b::transformerStartSequenceToken);
       onnxModelOptions.transformerEndSequenceToken().ifPresent(b::transformerEndSequenceToken);
       onnxModelOptions.transformerMaskToken().ifPresent(b::transformerMaskToken);
       onnxModelOptions.executionMode().ifPresent(value -> b.transformerExecutionMode(TransformerExecutionMode.Enum.valueOf(value)));
       onnxModelOptions.interOpThreads().ifPresent(b::transformerInterOpThreads);
       onnxModelOptions.intraOpThreads().ifPresent(b::transformerIntraOpThreads);
       onnxModelOptions.gpuDevice().ifPresent(value -> b.transformerGpuDevice(value.deviceNumber()));
    }

}
