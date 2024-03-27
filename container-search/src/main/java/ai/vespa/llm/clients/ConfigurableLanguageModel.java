// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.LlmClientConfig;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.logging.Logger;


/**
 * Base class for language models that can be configured with config definitions.
 *
 * @author lesters
 */
@Beta
public abstract class ConfigurableLanguageModel implements LanguageModel {

    private static Logger log = Logger.getLogger(ai.vespa.llm.clients.ConfigurableLanguageModel.class.getName());

    private final String apiKey;
    private final String endpoint;

    public ConfigurableLanguageModel() {
        this.apiKey = null;
        this.endpoint = null;
    }

    @Inject
    public ConfigurableLanguageModel(LlmClientConfig config, SecretStore secretStore) {
        this.apiKey = findApiKeyInSecretStore(config.apiKeySecretName(), secretStore);
        this.endpoint = config.endpoint();
    }

    private static String findApiKeyInSecretStore(String property, SecretStore secretStore) {
        String apiKey = "";
        if (property != null && ! property.isEmpty()) {
            try {
                apiKey = secretStore.getSecret(property);
            } catch (UnsupportedOperationException e) {
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
            params.setApiKey(apiKey);
        }
    }

    protected String getEndpoint() {
        return endpoint;
    }

    protected void setEndpoint(InferenceParameters params) {
        params.setEndpoint(endpoint);
    }

}
