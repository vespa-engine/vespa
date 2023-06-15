// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.component;

import com.yahoo.config.ModelReference;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.embedding.huggingface.HuggingFaceEmbedderConfig;
import com.yahoo.vespa.model.container.xml.ModelIdResolver;
import org.w3c.dom.Element;

import java.util.Optional;

import static com.yahoo.config.model.builder.xml.XmlHelper.getOptionalChild;
import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;


/**
 * @author bjorncs
 */
public class HuggingFaceEmbedder extends TypedComponent implements HuggingFaceEmbedderConfig.Producer {
    private final ModelReference model;
    private final ModelReference vocab;
    private final Integer maxTokens;
    private final String transformerInputIds;
    private final String transformerAttentionMask;
    private final String transformerTokenTypeIds;
    private final String transformerOutput;
    private final Boolean normalize;
    private final String onnxExecutionMode;
    private final Integer onnxInteropThreads;
    private final Integer onnxIntraopThreads;
    private final Integer onnxGpuDevice;
    private final String poolingStrategy;

    public HuggingFaceEmbedder(Element xml, DeployState state) {
        super("ai.vespa.embedding.huggingface.HuggingFaceEmbedder", INTEGRATION_BUNDLE_NAME, xml);
        var transformerModelElem = getOptionalChild(xml, "transformer-model").orElseThrow();
        model = ModelIdResolver.resolveToModelReference(transformerModelElem, state);
        vocab = getOptionalChild(xml, "tokenizer-model")
                .map(elem -> ModelIdResolver.resolveToModelReference(elem, state))
                .orElseGet(() -> resolveDefaultVocab(transformerModelElem, state));
        maxTokens = getChildValue(xml, "max-tokens").map(Integer::parseInt).orElse(null);
        transformerInputIds = getChildValue(xml, "transformer-input-ids").orElse(null);
        transformerAttentionMask = getChildValue(xml, "transformer-attention-mask").orElse(null);
        transformerTokenTypeIds = getChildValue(xml, "transformer-token-type-ids").orElse(null);
        transformerOutput = getChildValue(xml, "transformer-output").orElse(null);
        normalize = getChildValue(xml, "normalize").map(Boolean::parseBoolean).orElse(null);
        onnxExecutionMode = getChildValue(xml, "onnx-execution-mode").orElse(null);
        onnxInteropThreads = getChildValue(xml, "onnx-interop-threads").map(Integer::parseInt).orElse(null);
        onnxIntraopThreads = getChildValue(xml, "onnx-intraop-threads").map(Integer::parseInt).orElse(null);
        onnxGpuDevice = getChildValue(xml, "onnx-gpu-device").map(Integer::parseInt).orElse(null);
        poolingStrategy = getChildValue(xml, "pooling-strategy").orElse(null);
    }

    private static ModelReference resolveDefaultVocab(Element model, DeployState state) {
        if (state.isHosted() && model.hasAttribute("model-id")) {
            var implicitVocabId = model.getAttribute("model-id") + "-vocab";
            return ModelIdResolver.resolveToModelReference(
                    "tokenizer-model", Optional.of(implicitVocabId), Optional.empty(), Optional.empty(), state);
        }
        throw new IllegalArgumentException("'tokenizer-model' must be specified");
    }

    @Override
    public void getConfig(HuggingFaceEmbedderConfig.Builder b) {
        b.transformerModel(model).tokenizerPath(vocab);
        if (maxTokens != null) b.transformerMaxTokens(maxTokens);
        if (transformerInputIds != null) b.transformerInputIds(transformerInputIds);
        if (transformerAttentionMask != null) b.transformerAttentionMask(transformerAttentionMask);
        if (transformerTokenTypeIds != null) b.transformerTokenTypeIds(transformerTokenTypeIds);
        if (transformerOutput != null) b.transformerOutput(transformerOutput);
        if (normalize != null) b.normalize(normalize);
        if (onnxExecutionMode != null) b.transformerExecutionMode(
                HuggingFaceEmbedderConfig.TransformerExecutionMode.Enum.valueOf(onnxExecutionMode));
        if (onnxInteropThreads != null) b.transformerInterOpThreads(onnxInteropThreads);
        if (onnxIntraopThreads != null) b.transformerIntraOpThreads(onnxIntraopThreads);
        if (onnxGpuDevice != null) b.transformerGpuDevice(onnxGpuDevice);
        if (poolingStrategy != null) b.poolingStrategy(HuggingFaceEmbedderConfig.PoolingStrategy.Enum.valueOf(poolingStrategy));
    }
}
