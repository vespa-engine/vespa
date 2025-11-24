// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.model.deploy.DeployState;
import ai.vespa.embedding.config.VoyageAiEmbedderConfig;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import static ai.vespa.embedding.config.VoyageAiEmbedderConfig.DefaultInputType;
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
 *   <timeout>30000</timeout>
 *   <auto-detect-input-type>true</auto-detect-input-type>
 *   <max-idle-connections>5</max-idle-connections>
 *   <normalize>false</normalize>
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
    /**
     * Request timeout in milliseconds. Also serves as the bound for retry attempts -
     * retries will stop when the total elapsed time would exceed this timeout.
     */
    private final Integer timeout;
    /**
     * Maximum number of retry attempts. Provides a safety limit in addition to the
     * timeout-based bound to prevent excessive retry attempts.
     */
    private final Integer maxRetries;
    /**
     * Default input type (query vs document) used when auto-detection is disabled.
     * VoyageAI optimizes embeddings differently based on whether the text is a search query
     * or a document to be indexed.
     */
    private final String defaultInputType;
    /**
     * When true, automatically detects input type from the Embedder.Context destination.
     * If the destination contains "query", it uses "query" type; otherwise "document" type.
     */
    private final Boolean autoDetectInputType;
    private final Boolean truncate;
    /**
     * Maximum number of idle HTTP connections to keep in the connection pool.
     * Helps manage resource usage and connection reuse for better performance.
     */
    private final Integer maxIdleConnections;
    private final Boolean normalize;

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
        this.timeout = getChildValue(xml, "timeout").map(Integer::parseInt).orElse(null);
        this.maxRetries = getChildValue(xml, "max-retries").map(Integer::parseInt).orElse(null);
        this.defaultInputType = getChildValue(xml, "default-input-type").orElse(null);
        this.autoDetectInputType = getChildValue(xml, "auto-detect-input-type").map(Boolean::parseBoolean).orElse(null);
        this.truncate = getChildValue(xml, "truncate").map(Boolean::parseBoolean).orElse(null);
        this.maxIdleConnections = getChildValue(xml, "max-idle-connections").map(Integer::parseInt).orElse(null);
        this.normalize = getChildValue(xml, "normalize").map(Boolean::parseBoolean).orElse(null);

        // Validate configuration
        validate();
    }

    /**
     * Validate configuration values.
     */
    public void validate() {
        if (timeout != null && timeout < 1000) {
            throw new IllegalArgumentException(
                    "timeout must be at least 1000ms, got: " + timeout);
        }

        if (maxRetries != null && (maxRetries < 0 || maxRetries > 100)) {
            throw new IllegalArgumentException(
                    "max-retries must be between 0 and 100, got: " + maxRetries);
        }

        if (maxIdleConnections != null && (maxIdleConnections < 0 || maxIdleConnections > 100)) {
            throw new IllegalArgumentException(
                    "max-idle-connections must be between 0 and 100, got: " + maxIdleConnections);
        }

        if (defaultInputType != null) {
            String type = defaultInputType.toLowerCase();
            if (!type.equals("query") && !type.equals("document")) {
                throw new IllegalArgumentException(
                        "default-input-type must be 'query' or 'document', got: " + defaultInputType);
            }
        }

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
        if (timeout != null) {
            builder.timeout(timeout);
        }
        if (maxRetries != null) {
            builder.maxRetries(maxRetries);
        }
        if (defaultInputType != null) {
            builder.defaultInputType(DefaultInputType.Enum.valueOf(defaultInputType.toLowerCase()));
        }
        if (autoDetectInputType != null) {
            builder.autoDetectInputType(autoDetectInputType);
        }
        if (truncate != null) {
            builder.truncate(truncate);
        }
        if (maxIdleConnections != null) {
            builder.maxIdleConnections(maxIdleConnections);
        }
        if (normalize != null) {
            builder.normalize(normalize);
        }
    }
}
