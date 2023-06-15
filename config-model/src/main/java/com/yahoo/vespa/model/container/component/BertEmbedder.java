// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.component;

import com.yahoo.config.ModelReference;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.embedding.BertBaseEmbedderConfig;
import com.yahoo.vespa.model.container.xml.ModelIdResolver;
import org.w3c.dom.Element;

import static com.yahoo.text.XML.getChild;
import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;

/**
 * @author bjorncs
 */
public class BertEmbedder extends TypedComponent implements BertBaseEmbedderConfig.Producer {

    private final ModelReference model;
    private final ModelReference vocab;
    private final Integer maxTokens;
    private final String transformerInputIds;
    private final String transformerAttentionMask;
    private final String transformerTokenTypeIds;
    private final String transformerOutput;
    private final Integer tranformerStartSequenceToken;
    private final Integer transformerEndSequenceToken;
    private final String poolingStrategy;
    private final String onnxExecutionMode;
    private final Integer onnxInteropThreads;
    private final Integer onnxIntraopThreads;
    private final Integer onnxGpuDevice;


    public BertEmbedder(Element xml, DeployState state) {
        super("ai.vespa.embedding.BertBaseEmbedder", INTEGRATION_BUNDLE_NAME, xml);
        model = ModelIdResolver.resolveToModelReference(getChild(xml, "transformer-model"), state);
        vocab = ModelIdResolver.resolveToModelReference(getChild(xml, "tokenizer-vocab"), state);
        maxTokens = getChildValue(xml, "max-tokens").map(Integer::parseInt).orElse(null);
        transformerInputIds = getChildValue(xml, "transformer-input-ids").orElse(null);
        transformerAttentionMask = getChildValue(xml, "transformer-attention-mask").orElse(null);
        transformerTokenTypeIds = getChildValue(xml, "transformer-token-type-ids").orElse(null);
        transformerOutput = getChildValue(xml, "transformer-output").orElse(null);
        tranformerStartSequenceToken = getChildValue(xml, "transformer-start-sequence-token").map(Integer::parseInt).orElse(null);
        transformerEndSequenceToken = getChildValue(xml, "transformer-end-sequence-token").map(Integer::parseInt).orElse(null);
        poolingStrategy = getChildValue(xml, "pooling-strategy").orElse(null);
        onnxExecutionMode = getChildValue(xml, "onnx-execution-mode").orElse(null);
        onnxInteropThreads = getChildValue(xml, "onnx-interop-threads").map(Integer::parseInt).orElse(null);
        onnxIntraopThreads = getChildValue(xml, "onnx-intraop-threads").map(Integer::parseInt).orElse(null);
        onnxGpuDevice = getChildValue(xml, "onnx-gpu-device").map(Integer::parseInt).orElse(null);
    }

    @Override
    public void getConfig(BertBaseEmbedderConfig.Builder b) {
        b.transformerModel(model).tokenizerVocab(vocab);
        if (maxTokens != null) b.transformerMaxTokens(maxTokens);
        if (transformerInputIds != null) b.transformerInputIds(transformerInputIds);
        if (transformerAttentionMask != null) b.transformerAttentionMask(transformerAttentionMask);
        if (transformerTokenTypeIds != null) b.transformerTokenTypeIds(transformerTokenTypeIds);
        if (transformerOutput != null) b.transformerOutput(transformerOutput);
        if (tranformerStartSequenceToken != null) b.transformerStartSequenceToken(tranformerStartSequenceToken);
        if (transformerEndSequenceToken != null) b.transformerEndSequenceToken(transformerEndSequenceToken);
        if (poolingStrategy != null) b.poolingStrategy(BertBaseEmbedderConfig.PoolingStrategy.Enum.valueOf(poolingStrategy));
        if (onnxExecutionMode != null) b.onnxExecutionMode(BertBaseEmbedderConfig.OnnxExecutionMode.Enum.valueOf(onnxExecutionMode));
        if (onnxInteropThreads != null) b.onnxInterOpThreads(onnxInteropThreads);
        if (onnxIntraopThreads != null) b.onnxIntraOpThreads(onnxIntraopThreads);
        if (onnxGpuDevice != null) b.onnxGpuDevice(onnxGpuDevice);
    }
}
