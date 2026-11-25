// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import ai.vespa.embedding.config.GgufEmbedderConfig;
import com.yahoo.config.ModelReference;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import java.util.Locale;
import java.util.Set;

import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.GGUF_EMBEDDER_CLASS;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;
import static com.yahoo.vespa.model.container.component.EmbedderPrependConfig.parsePrependElement;
import static com.yahoo.vespa.model.container.xml.ModelIdResolver.GGUF_MODEL;

/**
 * The gguf-embedder component type, which embeds text with a GGUF model through llama.cpp.
 * The transformer-model element sets embeddingModel, max-tokens sets maxPromptTokens, pooling-strategy sets poolingType,
 * and prepend sets prependQuery and prependDocument.
 * Each other element sets the gguf-embedder config field of the same name in camel case.
 * An element that is not given leaves the config field's default.
 *
 * @author ingridhabrekke
 * @author glebashnik
 */
public class GgufEmbedder extends TypedComponent implements GgufEmbedderConfig.Producer {

    private final ModelReference embeddingModel;
    private final Integer maxPromptTokens;
    private final Boolean normalize;
    private final GgufEmbedderConfig.PoolingType.Enum poolingType;
    private final EmbedderPrependConfig prepend;
    private final Integer parallel;
    private final Integer contextSize;
    private final Integer logicalMaxBatchSize;
    private final Integer physicalMaxBatchSize;
    private final Integer gpuLayers;
    private final Integer seed;
    private final Double threads;
    private final Double batchThreads;

    public GgufEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super(GGUF_EMBEDDER_CLASS, INTEGRATION_BUNDLE_NAME, xml);
        embeddingModel = Model.fromXml(state, xml, "transformer-model", Set.of(GGUF_MODEL)).orElseThrow().modelReference();
        maxPromptTokens = getChildValue(xml, "max-tokens").map(Integer::parseInt).orElse(null);
        normalize = getChildValue(xml, "normalize").map(Boolean::parseBoolean).orElse(null);
        poolingType = getChildValue(xml, "pooling-strategy")
                .map(value -> GgufEmbedderConfig.PoolingType.Enum.valueOf(value.toUpperCase(Locale.ROOT)))
                .orElse(null);
        prepend = parsePrependElement(xml);
        parallel = getChildValue(xml, "parallel").map(Integer::parseInt).orElse(null);
        contextSize = getChildValue(xml, "context-size").map(Integer::parseInt).orElse(null);
        logicalMaxBatchSize = getChildValue(xml, "logical-max-batch-size").map(Integer::parseInt).orElse(null);
        physicalMaxBatchSize = getChildValue(xml, "physical-max-batch-size").map(Integer::parseInt).orElse(null);
        gpuLayers = getChildValue(xml, "gpu-layers").map(Integer::parseInt).orElse(null);
        seed = getChildValue(xml, "seed").map(Integer::parseInt).orElse(null);
        threads = getChildValue(xml, "threads").map(Double::parseDouble).orElse(null);
        batchThreads = getChildValue(xml, "batch-threads").map(Double::parseDouble).orElse(null);
    }

    @Override
    public void getConfig(GgufEmbedderConfig.Builder b) {
        b.embeddingModel(embeddingModel);
        if (maxPromptTokens != null) b.maxPromptTokens(maxPromptTokens);
        if (normalize != null) b.normalize(normalize);
        if (poolingType != null) b.poolingType(poolingType);
        if (prepend != null) prepend.applyTo(b::prependQuery, b::prependDocument);
        if (parallel != null) b.parallel(parallel);
        if (contextSize != null) b.contextSize(contextSize);
        if (logicalMaxBatchSize != null) b.logicalMaxBatchSize(logicalMaxBatchSize);
        if (physicalMaxBatchSize != null) b.physicalMaxBatchSize(physicalMaxBatchSize);
        if (gpuLayers != null) b.gpuLayers(gpuLayers);
        if (seed != null) b.seed(seed);
        if (threads != null) b.threads(threads);
        if (batchThreads != null) b.batchThreads(batchThreads);
    }

}
