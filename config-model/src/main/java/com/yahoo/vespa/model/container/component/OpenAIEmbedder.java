// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import ai.vespa.embedding.config.OpenaiEmbedderConfig;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;

/**
 * Configuration builder for OpenAI embedder component.
 *
 * @author bjorncs
 */
public class OpenAIEmbedder extends TypedComponent implements OpenaiEmbedderConfig.Producer {

    private final String apiKeySecretRef;
    private final String model;
    private final int dimensions;
    private final String endpoint;

    @SuppressWarnings("unused")
    public OpenAIEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super("ai.vespa.embedding.OpenAIEmbedder", INTEGRATION_BUNDLE_NAME, xml);
        this.apiKeySecretRef = getChildValue(xml, "api-key-secret-ref").orElse("");
        this.model = getChildValue(xml, "model").get();
        this.dimensions = getChildValue(xml, "dimensions")
                .map(Integer::parseInt).get();
        this.endpoint = getChildValue(xml, "endpoint").orElse(null);
    }

    @Override
    public void getConfig(OpenaiEmbedderConfig.Builder builder) {
        builder.apiKeySecretRef(apiKeySecretRef);
        builder.model(model);
        builder.dimensions(dimensions);
        if (endpoint != null) builder.endpoint(endpoint);
    }
}
