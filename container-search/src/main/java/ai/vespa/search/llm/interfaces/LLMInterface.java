package ai.vespa.search.llm.interfaces;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.search.llm.LlmInterfaceConfig;
import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

public abstract class LLMInterface implements LanguageModel {

    private static Logger log = Logger.getLogger(LLMInterface.class.getName());

    // Remove
    private final String apiKey;
    private final String endpoint;

    public LLMInterface(LlmInterfaceConfig config, SecretStore secretStore) {
        this.apiKey = findApiKeyInSecretStore(config.apiKey(), secretStore);  // is this implicitly assuming external store?
        this.endpoint = config.endpoint();
    }

    @Override
    public abstract List<Completion> complete(Prompt prompt, InferenceParameters options);

    @Override
    public abstract CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt,
                                                                             InferenceParameters options,
                                                                             Consumer<Completion> consumer);

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
        if (params.getApiKey().isPresent()) {
            return params.getApiKey().get();
        }
        return apiKey;
    }

    protected String getEndpoint() {
        return endpoint;
    }


}
