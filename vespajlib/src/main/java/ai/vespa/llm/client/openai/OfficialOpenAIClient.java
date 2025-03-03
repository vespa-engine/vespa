package ai.vespa.llm.client.openai;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.InferenceParameters;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatModel;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import ai.vespa.llm.LanguageModel;

public class OfficialOpenAIClient  implements LanguageModel{
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/";
     // These are public so that they can be used to set corresponding InferenceParameters outside of this class.
    public static final String OPTION_MODEL = "model";
    public static final String OPTION_TEMPERATURE = "temperature";
    public static final String OPTION_MAX_TOKENS = "maxTokens";

    
    @Override
    public List<Completion> complete(Prompt prompt, InferenceParameters options) {
        var apiKey = options.getApiKey().orElseThrow();
        var endpoint = options.getEndpoint().orElse(DEFAULT_ENDPOINT);
        OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(apiKey).baseUrl(endpoint).build();
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
            .model(ChatModel.of(DEFAULT_MODEL))
            .addUserMessage(prompt.toString());

        options.getInt(OPTION_MAX_TOKENS).ifPresent(builder::maxCompletionTokens);
        options.getDouble(OPTION_TEMPERATURE).ifPresent(builder::temperature);
        
        ChatCompletionCreateParams createParams = builder.build();
        
        List<Completion> completions = client.chat().completions().create(createParams).choices().stream()
                .flatMap(choice -> choice.message().content().stream())
                .map(content -> new Completion(content, Completion.FinishReason.stop))
                .toList();
        
        return completions;
    }

    @Override
    public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt, InferenceParameters options, Consumer<Completion> consumer) {
        var apiKey = options.getApiKey().orElseThrow();
        var endpoint = options.getEndpoint().orElse(DEFAULT_ENDPOINT);
        OpenAIClientAsync client = OpenAIOkHttpClientAsync.builder().apiKey(apiKey).baseUrl(endpoint).build();
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
            .model(ChatModel.of(DEFAULT_MODEL))
            .addUserMessage(prompt.toString());

        options.getInt(OPTION_MAX_TOKENS).ifPresent(builder::maxCompletionTokens);
        options.getDouble(OPTION_TEMPERATURE).ifPresent(builder::temperature);
        
        ChatCompletionCreateParams createParams = builder.build();
        
        CompletableFuture<Completion.FinishReason> future = new CompletableFuture<>();
        client.chat().completions().create(createParams).thenAccept(completion -> {
            completion.choices().stream()
                .flatMap(choice -> choice.message().content().stream())
                .map(content -> new Completion(content, Completion.FinishReason.stop))
                .forEach(consumer);
            future.complete(Completion.FinishReason.stop);
        }).join();
        return future;
    }

    // Private method that maps from Official OpenAI FinishReason to Vespa Completion.FinishReason
}