// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;

import java.util.logging.Logger;


/**
 * Base class for language models that can be configured with config definitions.
 *
 * @author lesters
 */
@Beta
public abstract class ConfigurableLanguageModel implements LanguageModel {

    private static final Logger log = Logger.getLogger(ConfigurableLanguageModel.class.getName());

    private final Secret apiKey;
    private final String endpoint;

    public ConfigurableLanguageModel() {
        this.apiKey = null;
        this.endpoint = null;
    }

    @Inject
    public ConfigurableLanguageModel(LlmClientConfig config, Secrets secretStore) {
        this.apiKey = findApiKeyInSecretStore(config.apiKeySecretName(), secretStore);
        this.endpoint = config.endpoint();
    }

    private static Secret findApiKeyInSecretStore(String property, Secrets secretStore) {
        Secret apiKey = null;
        if (property != null && ! property.isEmpty()) {
            try {
                apiKey = secretStore.get(property);
            } catch (UnsupportedOperationException e) {
                log.warning("Secrets is not set up: " + e.getMessage() + "\n" +
                        "Will expect API key in request header");
                // Secret store is not available - silently ignore this
            } catch (Exception e) {
                log.warning("Secret store look up failed: " + e.getMessage() + "\n" +
                        "Will expect API key in request header");
            }
        }
        return apiKey;
    }

    protected String getApiKey(InferenceParameters params) {
        return params.getApiKey().orElse(null);
    }

    /**
     * Set the API key as retrieved from secret store if it is not already set
     */
    protected void setApiKey(InferenceParameters params) {
        if (params.getApiKey().isEmpty() && apiKey != null) {
            params.setApiKey(apiKey.current());
        }
    }

    protected String getEndpoint() {
        return endpoint;
    }

    protected void setEndpoint(InferenceParameters params) {
        if (endpoint != null && ! endpoint.isEmpty()) {
            params.setEndpoint(endpoint);
        }
    }
}
