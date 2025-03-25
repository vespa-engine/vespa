// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModelException;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.secret.Secrets;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.ChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.JsonValue;
import com.openai.models.ResponseFormatJsonSchema;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A configurable OpenAI client that extends the {@link ConfigurableLanguageModel} class.
 * Uses Official OpenAI java client (https://github.com/openai/openai-java)
 * Supports basic completion and structured JSON output. Extensible to support Embedding, Tool Calling and Moderations.
 * Will reuse clients for Completions using same endpoint and API key to reduce connection overhead for multiple requests to the same endpoint with the same API key.
 * @author lesters
 * @author glebashnik
 * @author thomasht86
 */
@Beta
public class OpenAI extends ConfigurableLanguageModel {
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/";
    private static final String DEFAULT_API_KEY = "<YOUR_API_KEY>";
    
    private final Map<String, String> configOptions;
    
    // Instance-level reused clients with separate caching for each client type
    // Using package-private access for testing
    OpenAIClient defaultSyncClient; 
    String cachedSyncApiKey; 
    String cachedSyncEndpoint; 
    
    OpenAIClientAsync defaultAsyncClient; 
    String cachedAsyncApiKey; 
    String cachedAsyncEndpoint; 

    @Inject
    public OpenAI(LlmClientConfig config, Secrets secretStore) {
        super(config, secretStore);
        
        configOptions = new HashMap<>();

        if (!config.model().isBlank()) {
            configOptions.put(InferenceParameters.OPTION_MODEL, config.model());
        }

        if (config.temperature() >= 0) {
            configOptions.put(InferenceParameters.OPTION_TEMPERATURE, String.valueOf(config.temperature()));
        }

        if (config.maxTokens() >= 0) {
            configOptions.put(InferenceParameters.OPTION_MAX_TOKENS, String.valueOf(config.maxTokens()));
        }
    }
    
    private InferenceParameters prepareParameters(InferenceParameters parameters) {
        setApiKey(parameters);
        setEndpoint(parameters);
        return parameters.withDefaultOptions(configOptions::get);
    }
    
    OpenAIClient getSyncClient(String apiKey, String endpoint) {
        // If we have a cached client and the parameters match the cached parameters, reuse it
        if (defaultSyncClient != null && 
            apiKey != null && apiKey.equals(cachedSyncApiKey) &&
            endpoint != null && endpoint.equals(cachedSyncEndpoint)) {
            return defaultSyncClient;
        }
        
        // Different API key or endpoint, create new client
        defaultSyncClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(endpoint)
                .responseValidation(false)
                .build();
        
        // Update cached values after successful creation
        cachedSyncApiKey = apiKey;
        cachedSyncEndpoint = endpoint;
        
        return defaultSyncClient;
    }
    
    OpenAIClientAsync getAsyncClient(String apiKey, String endpoint) {
        // If we have a cached client and the parameters match the cached parameters, reuse it
        if (defaultAsyncClient != null && 
            apiKey != null && apiKey.equals(cachedAsyncApiKey) &&
            endpoint != null && endpoint.equals(cachedAsyncEndpoint)) {
            return defaultAsyncClient;
        }
        
        // Different API key or endpoint, create new client
        defaultAsyncClient = OpenAIOkHttpClientAsync.builder()
                .apiKey(apiKey)
                .baseUrl(endpoint)
                .responseValidation(false)
                .build();
        
        // Update cached values after successful creation
        cachedAsyncApiKey = apiKey;
        cachedAsyncEndpoint = endpoint;
        
        return defaultAsyncClient;
    }

    @Override
    public List<Completion> complete(Prompt prompt, InferenceParameters parameters) {
        var preparedParameters = prepareParameters(parameters);
        String apiKey = preparedParameters.getApiKey().orElse(DEFAULT_API_KEY);
        String endpoint = preparedParameters.getEndpoint().orElse(DEFAULT_ENDPOINT);
        
        OpenAIClient client = getSyncClient(apiKey, endpoint);
        
        ChatCompletionCreateParams createParams = getChatCompletionCreateParams(preparedParameters, prompt);
        
        return client.chat().completions().create(createParams).choices().stream()
                .flatMap(choice -> choice.message().content().stream()
                    .map(content -> new Completion(content, mapFinishReason(choice.finishReason().toString())))
                )
                .toList();
    }

    @Override
    public CompletableFuture<Completion.FinishReason> completeAsync(
            Prompt prompt, InferenceParameters parameters, Consumer<Completion> consumer) {
        var preparedParameters = prepareParameters(parameters);
        String apiKey = preparedParameters.getApiKey().orElse(DEFAULT_API_KEY);
        String endpoint = preparedParameters.getEndpoint().orElse(DEFAULT_ENDPOINT);
        
        OpenAIClientAsync client = getAsyncClient(apiKey, endpoint);
                
        ChatCompletionCreateParams createParams = getChatCompletionCreateParams(preparedParameters, prompt);
        
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
              });
        
        return future;
    }

    private ChatCompletionCreateParams getChatCompletionCreateParams(InferenceParameters parameters, Prompt prompt) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
            .model(ChatModel.of(parameters.get(InferenceParameters.OPTION_MODEL).map(Object::toString).orElse(DEFAULT_MODEL)))
            .addUserMessage(prompt.toString());
        parameters.getInt(InferenceParameters.OPTION_MAX_TOKENS).ifPresent(builder::maxCompletionTokens);
        parameters.getDouble(InferenceParameters.OPTION_TEMPERATURE).ifPresent(builder::temperature);
        parameters.getDouble(InferenceParameters.OPTION_TOP_P).ifPresent(builder::topP);
        parameters.getLong(InferenceParameters.OPTION_SEED).ifPresent(builder::seed);
        parameters.getInt(InferenceParameters.OPTION_N_PREDICT).ifPresent(builder::n);
        parameters.getDouble(InferenceParameters.OPTION_FREQUENCY_PENALTY).ifPresent(builder::frequencyPenalty);
        parameters.getDouble(InferenceParameters.OPTION_PRESENCE_PENALTY).ifPresent(builder::presencePenalty);
        // Add JSON schema if specified
        addResponseFormat(parameters, builder);
        
        return builder.build();
    }
    
    private void addResponseFormat(InferenceParameters parameters, ChatCompletionCreateParams.Builder builder) {
        parameters.get(InferenceParameters.OPTION_JSON_SCHEMA).ifPresent(jsonSchemaStr -> {
            try {
                ObjectMapper mapper = new ObjectMapper();  
                // Parse the JSON string to a Map using readValue
                Map<String, Object> rawMap = mapper.readValue(
                    jsonSchemaStr.toString(), 
                    new TypeReference<Map<String, Object>>() {}
                );
                Map<String, JsonValue> additionalProps = new HashMap<>();
                
                // Convert each value to JsonValue
                rawMap.forEach((key, value) -> 
                    additionalProps.put(key, JsonValue.from(value)));
                
                ResponseFormatJsonSchema.JsonSchema.Schema schema = 
                    ResponseFormatJsonSchema.JsonSchema.Schema.builder()
                        .putAllAdditionalProperties(additionalProps)
                        .build();
                
                var jsonFormat = ResponseFormatJsonSchema.builder()
                    .jsonSchema(ResponseFormatJsonSchema.JsonSchema.builder()
                        .name("structured-output")
                        .schema(schema)
                        .build())
                    .build();
                
                builder.responseFormat(jsonFormat);
            } catch (Exception e) {
                throw new LanguageModelException(400, "Failed to parse JSON schema:\n" + jsonSchemaStr.toString() + "\n" + e.getMessage(), e);
            }
        });
    }

    /**
     * Method to map from OpenAI library FinishReason (as string) to ai.vespa.llm.completion.Completion.FinishReason
     */
    private Completion.FinishReason mapFinishReason(String openAiFinishReason) {
        if (openAiFinishReason == null) return Completion.FinishReason.none;
        
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
