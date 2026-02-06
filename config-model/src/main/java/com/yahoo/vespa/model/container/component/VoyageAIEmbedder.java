// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.model.deploy.DeployState;
import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;

/**
 * Configuration builder for VoyageAI embedder component.
 * Parses XML configuration from services.xml and produces VoyageAiEmbedderConfig.
 *
 * @author VoyageAI team
 * @author bjorncs
 */
public class VoyageAIEmbedder extends TypedComponent implements VoyageAiEmbedderConfig.Producer {

    private final String apiKeySecretRef;
    private final String endpoint;
    private final String model;
    private final Boolean truncate;
    private final Integer dimensions;
    private final String quantization;

    @SuppressWarnings("unused")
    public VoyageAIEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super("ai.vespa.embedding.VoyageAIEmbedder", INTEGRATION_BUNDLE_NAME, xml);

        this.apiKeySecretRef = getChildValue(xml, "api-key-secret-ref").get();
        this.model = getChildValue(xml, "model").get();
        this.dimensions = getChildValue(xml, "dimensions")
                .map(Integer::parseInt)
                .orElseThrow(() -> new IllegalArgumentException("Missing required element 'dimensions'"));

        this.endpoint = getChildValue(xml, "endpoint").orElse(null);
        this.truncate = getChildValue(xml, "truncate").map(Boolean::parseBoolean).orElse(null);
        this.quantization = getChildValue(xml, "quantization").orElse("auto");

        validate();
    }

    public void validate() {
        if (model != null && !model.startsWith("voyage-")) {
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
        builder.quantization(VoyageAiEmbedderConfig.Quantization.Enum.valueOf(quantization.toUpperCase(java.util.Locale.ROOT)));

        if (endpoint != null) {
            builder.endpoint(endpoint);
        }
        if (truncate != null) {
            builder.truncate(truncate);
        }
    }
}
