package ai.vespa.llm.client.openai;

import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.InferenceParameters;
import com.yahoo.api.annotations.Beta;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.ChatModel;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import ai.vespa.llm.LanguageModel;

/**
 * An OpenAiClient that implements the LanguageModel interface using the official OpenAI Java client.
 * See https://github.com/openai/openai-java
 * Currently only basic completion is implemented, but it is extensible to support Structured Output, Embedding, Tool Calling and Moderations.
 * 
 * @author thomasht86
 */
@Beta
public class OpenAiClient implements LanguageModel {
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/";
    
    // Public option keys for configuration
    public static final String OPTION_MODEL = "model";
    public static final String OPTION_TEMPERATURE = "temperature";
    public static final String OPTION_MAX_TOKENS = "maxTokens";
    
    private final String defaultApiKey;
    private final String defaultEndpoint;
    
    // Instance-level reused clients for default API key and endpoint
    private OpenAIClient defaultSyncClient;
    private OpenAIClientAsync defaultAsyncClient;
    
    public OpenAiClient() {
        this(null, DEFAULT_ENDPOINT);
    }
    
    public OpenAiClient(String apiKey) {
        this(apiKey, DEFAULT_ENDPOINT);
    }
    
    public OpenAiClient(String apiKey, String endpoint) {
        this.defaultApiKey = apiKey;
        this.defaultEndpoint = Objects.requireNonNull(endpoint, "Endpoint cannot be null");
    }
    
    private OpenAIClient getSyncClient(String apiKey, String endpoint) {
        if (defaultApiKey != null && defaultApiKey.equals(apiKey) && defaultEndpoint.equals(endpoint)) {
            if (defaultSyncClient == null) {
                defaultSyncClient = OpenAIOkHttpClient.builder()
                        .apiKey(apiKey)
                        .baseUrl(endpoint)
                        .responseValidation(false) // Have to disable response validation to support other OpenAI compatible endpoints (some return additional fields)
                        .build();
            }
            return defaultSyncClient;
        }
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(endpoint)
                .responseValidation(false)
                .build();
    }
    
    private OpenAIClientAsync getAsyncClient(String apiKey, String endpoint) {
        if (defaultApiKey != null && defaultApiKey.equals(apiKey) && defaultEndpoint.equals(endpoint)) {
            if (defaultAsyncClient == null) {
                defaultAsyncClient = OpenAIOkHttpClientAsync.builder()
                        .apiKey(apiKey)
                        .baseUrl(endpoint)
                        .responseValidation(false)
                        .build();
            }
            return defaultAsyncClient;
        }
        return OpenAIOkHttpClientAsync.builder()
                .apiKey(apiKey)
                .baseUrl(endpoint)
                .responseValidation(false)
                .build();
    }
    
    @Override
    public List<Completion> complete(Prompt prompt, InferenceParameters options) {
        String apiKey = options.getApiKey().orElse(defaultApiKey);
        String endpoint = options.getEndpoint().orElse(defaultEndpoint);
        
        if (apiKey == null) {
            throw new IllegalArgumentException("API key must be provided either in constructor or in InferenceParameters");
        }
        
        OpenAIClient client = getSyncClient(apiKey, endpoint);
        
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
            .model(ChatModel.of(options.get(OPTION_MODEL).map(Object::toString).orElse(DEFAULT_MODEL)))
            .addUserMessage(prompt.toString());
        options.getInt(OPTION_MAX_TOKENS).ifPresent(builder::maxCompletionTokens);
        options.getDouble(OPTION_TEMPERATURE).ifPresent(builder::temperature);
        
        ChatCompletionCreateParams createParams = builder.build();
        
        return client.chat().completions().create(createParams).choices().stream()
                .flatMap(choice -> choice.message().content().stream()
                    .map(content -> new Completion(content, mapFinishReason(choice.finishReason().toString())))
                )
                .toList();
    }
    
    @Override
    public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt, InferenceParameters options, Consumer<Completion> consumer) {
        String apiKey = options.getApiKey().orElse(defaultApiKey);
        String endpoint = options.getEndpoint().orElse(defaultEndpoint);
        
        if (apiKey == null) {
            throw new IllegalArgumentException("API key must be provided either in constructor or in InferenceParameters");
        }
        
        OpenAIClientAsync client = getAsyncClient(apiKey, endpoint);
        
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
            .model(ChatModel.of(options.get(OPTION_MODEL).map(Object::toString).orElse(DEFAULT_MODEL)))
            .addUserMessage(prompt.toString());
        options.getInt(OPTION_MAX_TOKENS).ifPresent(builder::maxCompletionTokens);
        options.getDouble(OPTION_TEMPERATURE).ifPresent(builder::temperature);
        
        ChatCompletionCreateParams createParams = builder.build();
        
        final Completion.FinishReason[] lastFinishReasonHolder = new Completion.FinishReason[]{Completion.FinishReason.stop};
        CompletableFuture<Completion.FinishReason> future = new CompletableFuture<>();
                
        // Use streaming API
        client.chat()
                .completions()
                .createStreaming(createParams)
                .subscribe(completion -> completion.choices().stream()
                    .flatMap(choice -> {
                        // Capture the finish reason if present
                        choice.finishReason().ifPresent(fr -> {
                            lastFinishReasonHolder[0] = mapFinishReason(fr.toString());
                        });
                        // Process delta content
                        return choice.delta().content().stream()
                            .map(content -> new Completion(content, 
                                choice.finishReason().map(fr -> mapFinishReason(fr.toString())).orElse(Completion.FinishReason.none)
                            ));
                    })
                    .forEach(consumer))
                .onCompleteFuture()
              .thenAccept(unused -> {
                  // When the stream completes, resolve the future with the last known finish reason
                  future.complete(lastFinishReasonHolder[0]);
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
            case "none" -> Completion.FinishReason.none;
            case "error" -> throw new IllegalStateException("OpenAI-client returned finish_reason=error");
            default -> Completion.FinishReason.other;
        };
    }
}