package ai.vespa.llm.client.openai;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.InferenceParameters;
import com.yahoo.api.annotations.Beta;

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

/**
 * An implementation of the LanguageModel interface using the official OpenAI Java client.
 * See https://github.com/openai/openai-java
 * Currently only basic completion is implemented, but it is extensible to support Structured Output, Tool Calling and Moderations.
 * 
 * @author thomasht86
 */
@Beta
public class OpenAiClient  implements LanguageModel{
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
                .flatMap(choice -> choice.message().content().stream()
                    .map(content -> new Completion(content, mapFinishReason(choice.finishReason().toString())))
                )
                .toList();
        
        return completions;
    }

    @Override
    public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt, InferenceParameters options, Consumer<Completion> consumer) {
        var apiKey = options.getApiKey().orElseThrow();
        var endpoint = options.getEndpoint().orElse(DEFAULT_ENDPOINT);
        OpenAIClientAsync client = OpenAIOkHttpClientAsync.builder().apiKey(apiKey).baseUrl(endpoint).build();
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
            .model(ChatModel.of(options.get(OPTION_MODEL).map(Object::toString).orElse(DEFAULT_MODEL)))
            .addUserMessage(prompt.toString());

        options.getInt(OPTION_MAX_TOKENS).ifPresent(builder::maxCompletionTokens);
        options.getDouble(OPTION_TEMPERATURE).ifPresent(builder::temperature);
        
        ChatCompletionCreateParams createParams = builder.build();
        
        CompletableFuture<Completion.FinishReason> future = new CompletableFuture<>();
        
        // Use streaming API
        client.chat()
              .completions()
              .createStreaming(createParams)
              .subscribe(completion -> completion.choices().stream()
                      .flatMap(choice -> {
                          // Process delta content
                          return choice.delta().content().stream()
                                  .map(content -> new Completion(content, 
                                          // Only set actual finish reason on last chunk
                                          choice.finishReason() != null ? 
                                                  mapFinishReason(choice.finishReason().toString()) : 
                                                  Completion.FinishReason.other));  // Use 'other' for streaming chunks
                      })
                      .forEach(consumer))
              .onCompleteFuture()
              .thenAccept(lastCompletion -> {
                  // When the stream completes, resolve the future with the final finish reason
                  Completion.FinishReason finalReason = lastCompletion != null ? 
                          mapFinishReason(lastCompletion.toString()) : 
                          Completion.FinishReason.stop;
                  future.complete(finalReason);
              })
              .exceptionally(e -> {
                  future.completeExceptionally(e);
                  return null;
              }).join();
        
        return future;
    }

    /**
     * Method to map from OpenAI library FinishReason (as string) to ai.vespa.llm.completion.Completion.FinishReason
     */
    private Completion.FinishReason mapFinishReason(String openAiFinishReason) {
        if (openAiFinishReason == null) return Completion.FinishReason.stop;
        
        return switch (openAiFinishReason) {
            case "stop" -> Completion.FinishReason.stop;
            case "length" -> Completion.FinishReason.length;
            case "content_filter" -> Completion.FinishReason.content_filter;
            case "tool_calls" -> Completion.FinishReason.tool_calls; 
            case "function_call" -> Completion.FinishReason.function_call; 
            case "error" -> throw new IllegalStateException("OpenAI-client returned finish_reason=error");
            default -> Completion.FinishReason.other;
        };
    }
}