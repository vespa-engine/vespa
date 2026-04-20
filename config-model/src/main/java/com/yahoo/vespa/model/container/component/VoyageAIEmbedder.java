// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import java.util.Locale;

import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;
import static com.yahoo.vespa.model.container.component.EmbedderBatchingConfig.parseBatchingElement;

/**
 * Configuration builder for VoyageAI embedder component.
 *
 * @author bjorncs
 */
public class VoyageAIEmbedder extends TypedComponent implements VoyageAiEmbedderConfig.Producer {

    private final String apiKeySecretRef;
    private final String model;
    private final int dimensions;
    private final String endpoint;
    private final String quantization;
    private final Boolean truncate;
    private final EmbedderBatchingConfig batching;

    @SuppressWarnings("unused")
    public VoyageAIEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super("ai.vespa.embedding.VoyageAIEmbedder", INTEGRATION_BUNDLE_NAME, xml);

        this.apiKeySecretRef = getChildValue(xml, "api-key-secret-ref").get();
        this.model = getChildValue(xml, "model").get();
        this.dimensions = getChildValue(xml, "dimensions")
                .map(Integer::parseInt).get();
        this.endpoint = getChildValue(xml, "endpoint").orElse(null);
        this.quantization = getChildValue(xml, "quantization").orElse(null);
        this.truncate = getChildValue(xml, "truncate").map(Boolean::parseBoolean).orElse(null);
        this.batching = parseBatchingElement(xml);

        if (!model.startsWith("voyage-")) {
            throw new IllegalArgumentException(
                    "Invalid VoyageAI model name: " + model + ". " +
                    "Model name should start with 'voyage-' (e.g., voyage-3, voyage-code-3).");
        }
    }

    @Override
    public void getConfig(VoyageAiEmbedderConfig.Builder builder) {
        builder.apiKeySecretRef(apiKeySecretRef);
        builder.model(model);
        builder.dimensions(dimensions);
        if (endpoint != null) builder.endpoint(endpoint);
        if (quantization != null) {
            builder.quantization(VoyageAiEmbedderConfig.Quantization.Enum.valueOf(quantization.toUpperCase(Locale.ROOT)));
        }
        if (truncate != null) builder.truncate(truncate);
        if (batching != null) batching.applyTo(builder.batching::maxSize, builder.batching::maxDelayMillis);
    }
}
