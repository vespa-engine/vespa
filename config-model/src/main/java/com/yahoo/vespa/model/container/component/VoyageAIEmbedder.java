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
 * <p>Example services.xml:
 * <pre>{@code
 * <component id="voyage-embedder" type="voyage-ai-embedder">
 *   <model>voyage-3</model>
 *   <api-key-secret-ref>voyage_api_key</api-key-secret-ref>
 *   <endpoint>https://api.voyageai.com/v1/embeddings</endpoint>
 * </component>
 * }</pre>
 *
 * <p><b>Note:</b> Request batching is not currently implemented. Each embed() call
 * results in a separate API request. Batching support will be added in a future version.
 *
 * @author VoyageAI team
 */
public class VoyageAIEmbedder extends TypedComponent implements VoyageAiEmbedderConfig.Producer {

    private final String apiKeySecretRef;
    /**
     * VoyageAI API endpoint URL. Can be overridden for:
     * <ul>
     * <li>Using a custom proxy/gateway for API requests</li>
     * <li>Testing with a mock server</li>
     * <li>Using regional endpoints if available</li>
     * </ul>
     * Default: https://api.voyageai.com/v1/embeddings
     */
    private final String endpoint;
    private final String model;
    private final Boolean truncate;

    @SuppressWarnings("unused") // cluster and state parameters required by Vespa component framework
    public VoyageAIEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super("ai.vespa.embedding.VoyageAIEmbedder", INTEGRATION_BUNDLE_NAME, xml);

        // Required fields
        this.apiKeySecretRef = getChildValue(xml, "api-key-secret-ref")
                .orElseThrow(() -> new IllegalArgumentException(
                        "VoyageAI embedder requires <api-key-secret-ref> element. " +
                        "Please specify the reference to the secret in Vespa's secret store."));
        this.model = getChildValue(xml, "model")
                .orElseThrow(() -> new IllegalArgumentException(
                        "VoyageAI embedder requires <model> element. " +
                        "Please specify the VoyageAI model name (e.g., voyage-3, voyage-3.5, voyage-code-3)."));

        // Optional fields with defaults
        this.endpoint = getChildValue(xml, "endpoint").orElse(null);
        this.truncate = getChildValue(xml, "truncate").map(Boolean::parseBoolean).orElse(null);

        // Validate configuration
        validate();
    }

    /**
     * Validate configuration values.
     */
    public void validate() {
        if (model != null && !model.startsWith("voyage")) {
            throw new IllegalArgumentException(
                    "Invalid VoyageAI model name: " + model + ". " +
                    "Model name should start with 'voyage' (e.g., voyage-3, voyage-code-3).");
        }
    }

    @Override
    public void getConfig(VoyageAiEmbedderConfig.Builder builder) {
        // Required
        builder.apiKeySecretRef(apiKeySecretRef);
        builder.model(model);

        // Optional - only set if provided (otherwise use defaults from .def file)
        if (endpoint != null) {
            builder.endpoint(endpoint);
        }
        if (truncate != null) {
            builder.truncate(truncate);
        }
    }
}
