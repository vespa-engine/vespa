// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.component;
import com.yahoo.config.ModelReference;
import com.yahoo.config.model.api.OnnxModelOptions;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.embedding.SpladeEmbedderConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import java.util.Set;

import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;
import static com.yahoo.vespa.model.container.xml.ModelIdResolver.HF_TOKENIZER;
import static com.yahoo.vespa.model.container.xml.ModelIdResolver.ONNX_MODEL;

public class SpladeEmbedder extends TypedComponent implements SpladeEmbedderConfig.Producer {

    private final OnnxModelOptions onnxModelOptions;
    private final ModelReference modelRef;
    private final ModelReference vocabRef;
    private final Integer maxTokens;
    private final String transformerInputIds;
    private final String transformerAttentionMask;
    private final String transformerTokenTypeIds;
    private final String transformerOutput;
    private final Double termScoreThreshold;

    public SpladeEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super("ai.vespa.embedding.SpladeEmbedder", INTEGRATION_BUNDLE_NAME, xml);
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
        termScoreThreshold = getChildValue(xml, "term-score-threshold").map(Double::parseDouble).orElse(null);
        model.registerOnnxModelCost(cluster, onnxModelOptions);
    }

    @Override
    public void getConfig(SpladeEmbedderConfig.Builder b) {
        b.transformerModel(modelRef).tokenizerPath(vocabRef);
        if (maxTokens != null) b.transformerMaxTokens(maxTokens);
        if (transformerInputIds != null) b.transformerInputIds(transformerInputIds);
        if (transformerAttentionMask != null) b.transformerAttentionMask(transformerAttentionMask);
        if (transformerTokenTypeIds != null) b.transformerTokenTypeIds(transformerTokenTypeIds);
        if (transformerOutput != null) b.transformerOutput(transformerOutput);
        if (termScoreThreshold != null) b.termScoreThreshold(termScoreThreshold);

        onnxModelOptions.executionMode().ifPresent(value -> b.transformerExecutionMode(SpladeEmbedderConfig.TransformerExecutionMode.Enum.valueOf(value)));
        onnxModelOptions.interOpThreads().ifPresent(b::transformerInterOpThreads);
        onnxModelOptions.intraOpThreads().ifPresent(b::transformerIntraOpThreads);
        onnxModelOptions.gpuDevice().ifPresent(value -> b.transformerGpuDevice(value.deviceNumber()));
    }

}

