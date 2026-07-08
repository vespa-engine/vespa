// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import ai.vespa.embedding.config.MistralEmbedderConfig;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import java.util.Locale;

import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;
import static com.yahoo.vespa.model.container.component.EmbedderBatchingConfig.parseBatchingElement;

/**
 * Configuration builder for Mistral embedder component.
 *
 * @author bjorncs
 */
public class MistralEmbedder extends TypedComponent implements MistralEmbedderConfig.Producer {

    private final String apiKeySecretRef;
    private final String model;
    private final int dimensions;
    private final String quantization;
    private final EmbedderBatchingConfig batching;

    @SuppressWarnings("unused")
    public MistralEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super("ai.vespa.embedding.MistralEmbedder", INTEGRATION_BUNDLE_NAME, xml);
        this.apiKeySecretRef = getChildValue(xml, "api-key-secret-ref").get();
        this.model = getChildValue(xml, "model").get();
        this.dimensions = getChildValue(xml, "dimensions")
                .map(Integer::parseInt).get();
        this.quantization = getChildValue(xml, "quantization").orElse(null);
        this.batching = parseBatchingElement(xml);
    }

    @Override
    public void getConfig(MistralEmbedderConfig.Builder builder) {
        builder.apiKeySecretRef(apiKeySecretRef);
        builder.model(model);
        builder.dimensions(dimensions);
        if (quantization != null) {
            builder.quantization(MistralEmbedderConfig.Quantization.Enum.valueOf(quantization.toUpperCase(Locale.ROOT)));
        }
        if (batching != null) batching.applyTo(builder.batching::maxSize, builder.batching::maxDelayMillis);
    }
}
