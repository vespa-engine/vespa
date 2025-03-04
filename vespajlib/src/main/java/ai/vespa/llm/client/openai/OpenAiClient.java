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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import ai.vespa.llm.LanguageModel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An OpenAiClient that implements the LanguageModel interface using the official OpenAI Java client.
 * See https://github.com/openai/openai-java
 * Currently only basic completion is implemented, but it is extensible to support Structured Output, Tool Calling and Moderations.
 * 
 * @author thomasht86
 */
@Beta
public class OpenAiClient implements LanguageModel {
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/";
    // These are public so that they can be used to set corresponding InferenceParameters outside of this class.
    public static final String OPTION_MODEL = "model";
    public static final String OPTION_TEMPERATURE = "temperature";
    public static final String OPTION_MAX_TOKENS = "maxTokens";
    
    private final String defaultApiKey;
    private final String defaultEndpoint;
    
    // Lazily initialized clients for default configuration
    private volatile OpenAIClient defaultSyncClient;
    private volatile OpenAIClientAsync defaultAsyncClient;
    
    static {
        // Register shutdown hook for proper cleanup of all client instances
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // This will be empty for now but will be used by the static method
            // to clean up any resources if needed in the future
        }));
    }
    
    /**
     * Creates an OpenAiClient with default configuration.
     * API key must be provided in InferenceParameters for each call.
     */
    public OpenAiClient() {
        this(null, DEFAULT_ENDPOINT);
    }
    
    /**
     * Creates an OpenAiClient with the given API key and default endpoint.
     * 
     * @param apiKey The OpenAI API key
     */
    public OpenAiClient(String apiKey) {
        this(apiKey, DEFAULT_ENDPOINT);
    }
    
    /**
     * Creates an OpenAiClient with the given API key and endpoint.
     * 
     * @param apiKey The OpenAI API key
     * @param endpoint The OpenAI API endpoint
     */
    public OpenAiClient(String apiKey, String endpoint) {
        this.defaultApiKey = apiKey;
        this.defaultEndpoint = Objects.requireNonNull(endpoint, "Endpoint cannot be null");
    }
    
    private synchronized OpenAIClient getSyncClient() {
        if (defaultSyncClient == null && defaultApiKey != null) {
            defaultSyncClient = OpenAIOkHttpClient.builder()
                                  .apiKey(defaultApiKey)
                                  .baseUrl(defaultEndpoint)
                                  .build();
        }
        return defaultSyncClient;
    }
    
    private synchronized OpenAIClientAsync getAsyncClient() {
        if (defaultAsyncClient == null && defaultApiKey != null) {
            defaultAsyncClient = OpenAIOkHttpClientAsync.builder()
                                     .apiKey(defaultApiKey)
                                     .baseUrl(defaultEndpoint)
                                     .build();
        }
        return defaultAsyncClient;
    }
    
    @Override
    public List<Completion> complete(Prompt prompt, InferenceParameters options) {
        OpenAIClient client;
        String apiKey = options.getApiKey().orElse(defaultApiKey);
        String endpoint = options.getEndpoint().orElse(defaultEndpoint);
        
        if (apiKey == null) {
            throw new IllegalArgumentException("API key must be provided either in constructor or in InferenceParameters");
        }
        
        // Reuse default client if possible
        if (apiKey.equals(defaultApiKey) && endpoint.equals(defaultEndpoint)) {
            client = getSyncClient();
            if (client == null) {
                // Create client if needed (this would happen when defaultApiKey was null initially)
                client = OpenAIOkHttpClient.builder()
                            .apiKey(apiKey)
                            .baseUrl(endpoint)
                            .build();
                defaultSyncClient = client;
            }
        } else {
            // Create a new client for this specific request
            client = OpenAIOkHttpClient.builder()
                        .apiKey(apiKey)
                        .baseUrl(endpoint)
                        .build();
        }
        
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
            .model(ChatModel.of(options.get(OPTION_MODEL).map(Object::toString).orElse(DEFAULT_MODEL)))
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
        OpenAIClientAsync client;
        String apiKey = options.getApiKey().orElse(defaultApiKey);
        String endpoint = options.getEndpoint().orElse(defaultEndpoint);
        
        if (apiKey == null) {
            throw new IllegalArgumentException("API key must be provided either in constructor or in InferenceParameters");
        }
        
        // Reuse default client if possible
        if (apiKey.equals(defaultApiKey) && endpoint.equals(defaultEndpoint)) {
            client = getAsyncClient();
            if (client == null) {
                // Create client if needed
                client = OpenAIOkHttpClientAsync.builder()
                            .apiKey(apiKey)
                            .baseUrl(endpoint)
                            .build();
                defaultAsyncClient = client;
            }
        } else {
            // Create a new client for this specific request
            client = OpenAIOkHttpClientAsync.builder()
                        .apiKey(apiKey)
                        .baseUrl(endpoint)
                        .build();
        }
        
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