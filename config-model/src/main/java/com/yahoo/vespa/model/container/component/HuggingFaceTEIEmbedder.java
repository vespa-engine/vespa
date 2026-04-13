// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import ai.vespa.embedding.config.HuggingFaceTeiEmbedderConfig;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import org.w3c.dom.Element;

import java.util.Locale;

import static com.yahoo.text.XML.getChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME;
import static com.yahoo.vespa.model.container.component.EmbedderBatchingConfig.parseBatchingElement;

/**
 * Parses XML configuration from services.xml and produces HuggingFaceTeiEmbedderConfig.
 */
public class HuggingFaceTEIEmbedder extends TypedComponent implements HuggingFaceTeiEmbedderConfig.Producer {

    private final String endpoint;
    private final String apiKeySecretRef;
    private final Integer dimensions;
    private final Boolean normalize;
    private final Boolean truncate;
    private final String truncationDirection;
    private final String promptName;
    private final String queryPromptName;
    private final String documentPromptName;
    private final Integer maxRetries;
    private final Integer timeout;
    private final EmbedderBatchingConfig batching;

    @SuppressWarnings("unused")
    public HuggingFaceTEIEmbedder(ApplicationContainerCluster cluster, Element xml, DeployState state) {
        super("ai.vespa.embedding.HuggingFaceTEIEmbedder", INTEGRATION_BUNDLE_NAME, xml);

        endpoint = getChildValue(xml, "endpoint").orElse(null);
        apiKeySecretRef = getChildValue(xml, "api-key-secret-ref").orElse(null);
        dimensions = getChildValue(xml, "dimensions").map(Integer::parseInt).orElse(null);
        normalize = getChildValue(xml, "normalize").map(Boolean::parseBoolean).orElse(null);
        truncate = getChildValue(xml, "truncate").map(Boolean::parseBoolean).orElse(null);
        truncationDirection = getChildValue(xml, "truncation-direction").orElse(null);
        promptName = getChildValue(xml, "prompt-name").orElse(null);
        queryPromptName = getChildValue(xml, "query-prompt-name").orElse(null);
        documentPromptName = getChildValue(xml, "document-prompt-name").orElse(null);
        maxRetries = getChildValue(xml, "max-retries").map(Integer::parseInt).orElse(null);
        timeout = getChildValue(xml, "timeout").map(Integer::parseInt).orElse(null);
        batching = parseBatchingElement(xml);

        validateConfig();
    }

    private void validateConfig() {
        if (dimensions != null && dimensions < 0)
            throw new IllegalArgumentException("dimensions must be non-negative, got: " + dimensions);
        if (maxRetries != null && maxRetries < 0)
            throw new IllegalArgumentException("max-retries must be non-negative, got: " + maxRetries);
        if (timeout != null && timeout <= 0)
            throw new IllegalArgumentException("timeout must be positive, got: " + timeout);
        if (truncationDirection != null) {
            var value = truncationDirection.toUpperCase(Locale.ROOT);
            if (!value.equals("LEFT") && !value.equals("RIGHT"))
                throw new IllegalArgumentException("truncation-direction must be one of [left, right], got: " + truncationDirection);
        }
    }

    @Override
    public void getConfig(HuggingFaceTeiEmbedderConfig.Builder builder) {
        if (endpoint != null) builder.endpoint(endpoint);
        if (apiKeySecretRef != null) builder.apiKeySecretRef(apiKeySecretRef);
        if (dimensions != null) builder.dimensions(dimensions);
        if (normalize != null) builder.normalize(normalize);
        if (truncate != null) builder.truncate(truncate);
        if (truncationDirection != null) {
            builder.truncationDirection(HuggingFaceTeiEmbedderConfig.TruncationDirection.Enum
                    .valueOf(truncationDirection.toUpperCase(Locale.ROOT)));
        }
        if (promptName != null) builder.promptName(promptName);
        if (queryPromptName != null) builder.queryPromptName(queryPromptName);
        if (documentPromptName != null) builder.documentPromptName(documentPromptName);
        if (maxRetries != null) builder.maxRetries(maxRetries);
        if (timeout != null) builder.timeout(timeout);
        if (batching != null) {
            builder.batching.maxSize(batching.maxSize());
            builder.batching.maxDelayMillis(batching.maxDelay().toMillis());
        }
    }
}
