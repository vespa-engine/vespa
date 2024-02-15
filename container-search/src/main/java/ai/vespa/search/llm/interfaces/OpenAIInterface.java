package ai.vespa.search.llm.interfaces;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.client.openai.OpenAiClient;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.search.llm.LlmInterfaceConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.secretstore.SecretStore;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class OpenAIInterface extends LLMInterface {

    private final OpenAiClient client;

    @Inject
    public OpenAIInterface(LlmInterfaceConfig config, SecretStore secretStore) {
        super(config, secretStore);
        client = new OpenAiClient();
    }

    @Override
    public List<Completion> complete(Prompt prompt, InferenceParameters parameters) {
        return client.complete(prompt, parameters);
    }

    @Override
    public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt,
                                                                    InferenceParameters parameters,
                                                                    Consumer<Completion> consumer) {
        // What about api key?
        return client.completeAsync(prompt, parameters, consumer);
    }
}
