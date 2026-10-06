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
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.SseException;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.ReasoningEffort;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A configurable OpenAI client that extends the {@link ConfigurableLanguageModel} class.
 * Uses Official OpenAI java client (https://github.com/openai/openai-java)
 * Supports basic completion and structured JSON output. Extensible to support Embedding, Tool Calling and Moderations.
 * Will reuse clients for Completions using same endpoint and API key to reduce connection overhead for multiple requests to the same endpoint with the same API key.
 * Failures from the OpenAI SDK are reported as {@link LanguageModelException} with an HTTP-like status code,
 * see toLanguageModelException for the mapping.
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
    private final boolean jsonSchemaStrict;
    
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
        jsonSchemaStrict = config.jsonSchemaStrict();

        if (!config.model().isBlank()) {
            configOptions.put(InferenceParameters.OPTION_MODEL, config.model());
        }

        if (config.temperature() >= 0) {
            configOptions.put(InferenceParameters.OPTION_TEMPERATURE, String.valueOf(config.temperature()));
        }

        if (config.maxTokens() >= 0) {
            configOptions.put(InferenceParameters.OPTION_MAX_TOKENS, String.valueOf(config.maxTokens()));
        }

        if (!config.reasoningEffort().isBlank()) {
            configOptions.put(InferenceParameters.OPTION_REASONING_EFFORT, config.reasoningEffort());
        }
    }
    
    // Package-private for testing
    InferenceParameters prepareParameters(InferenceParameters parameters) {
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
        try {
            var preparedParameters = prepareParameters(parameters);
            String apiKey = preparedParameters.getApiKey().orElse(DEFAULT_API_KEY);
            String endpoint = preparedParameters.getEndpoint().orElse(DEFAULT_ENDPOINT);
            ChatCompletionCreateParams createParams = getChatCompletionCreateParams(preparedParameters, prompt);
            OpenAIClient client = getSyncClient(apiKey, endpoint);

            List<Completion> completions = new ArrayList<>();
            for (var choice : client.chat().completions().create(createParams).choices()) {
                var finishReason = mapFinishReason(choice.finishReason().toString());
                if (finishReason == Completion.FinishReason.error)
                    throw new LanguageModelException(502, endpoint + " reported an error generating the completion");
                choice.message().content().ifPresent(content -> completions.add(new Completion(content, finishReason)));
            }
            return completions;
        } catch (OpenAIException | IllegalArgumentException e) {
            throw toLanguageModelException(e);
        }
    }

    @Override
    public CompletableFuture<Completion.FinishReason> completeAsync(
            Prompt prompt, InferenceParameters parameters, Consumer<Completion> consumer) {
        CompletableFuture<Completion.FinishReason> future = new CompletableFuture<>();
        // Set when the provider sends a finish reason. Stays null if the stream ends before that,
        // e.g. because the connection was cut, in which case the answer is incomplete.
        AtomicReference<Completion.FinishReason> finishReason = new AtomicReference<>();
        // Set if the consumer throws. The SDK wraps such exceptions as its own, so they are kept here
        // to be reported as they are instead of as failures of the request.
        AtomicReference<RuntimeException> consumerException = new AtomicReference<>();

        // Every failure is reported through the returned future, nothing is thrown from this method.
        try {
            var preparedParameters = prepareParameters(parameters);
            String apiKey = preparedParameters.getApiKey().orElse(DEFAULT_API_KEY);
            String endpoint = preparedParameters.getEndpoint().orElse(DEFAULT_ENDPOINT);
            ChatCompletionCreateParams createParams = getChatCompletionCreateParams(preparedParameters, prompt);
            OpenAIClientAsync client = getAsyncClient(apiKey, endpoint);

            client.chat()
                    .completions()
                    .createStreaming(createParams)
                    .subscribe(chunk -> handleChunk(chunk, finishReason, consumer, consumerException))
                    .onCompleteFuture()
                    .whenComplete((unused, error) -> {
                        if (consumerException.get() != null) {
                            future.completeExceptionally(consumerException.get());
                        } else if (error != null) {
                            Throwable cause = unwrap(error);
                            future.completeExceptionally(
                                    cause instanceof OpenAIException ? toLanguageModelException(cause) : cause);
                        } else if (finishReason.get() == null) {
                            future.completeExceptionally(new LanguageModelException(502,
                                    "Stream from " + endpoint + " ended before a finish reason was received"));
                        } else if (finishReason.get() == Completion.FinishReason.error) {
                            future.completeExceptionally(new LanguageModelException(502,
                                    endpoint + " reported an error generating the completion"));
                        } else {
                            future.complete(finishReason.get());
                        }
                    });
        } catch (OpenAIException | IllegalArgumentException e) {
            future.completeExceptionally(toLanguageModelException(e));
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private void handleChunk(ChatCompletionChunk chunk,
                             AtomicReference<Completion.FinishReason> finishReason,
                             Consumer<Completion> consumer,
                             AtomicReference<RuntimeException> consumerException) {
        for (var choice : chunk.choices()) {
            var choiceFinishReason = choice.finishReason().map(fr -> mapFinishReason(fr.toString()));
            choiceFinishReason.ifPresent(finishReason::set);
            var content = choice.delta().content();
            if (content.isEmpty()) continue;
            try {
                consumer.accept(new Completion(content.get(), choiceFinishReason.orElse(Completion.FinishReason.none)));
            } catch (RuntimeException e) {
                consumerException.set(e);
                throw e;  // Stops the stream
            }
        }
    }

    /**
     * Maps an exception from the OpenAI SDK, or an IllegalArgumentException from building the client or request,
     * to a LanguageModelException with a status code:
     * <ul>
     *   <li>the status returned by the provider for HTTP errors (401, 429, 5xx, ...)</li>
     *   <li>502 for an error event in a stream or a response that could not be parsed</li>
     *   <li>503 if the provider could not be reached, 504 if it timed out</li>
     *   <li>400 for an invalid endpoint or request</li>
     * </ul>
     * The original exception is kept as the cause, and its message is used as-is so that it is
     * not repeated when the messages of the cause chain are rendered.
     */
    static LanguageModelException toLanguageModelException(Throwable e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (e instanceof SseException)
            return new LanguageModelException(502, message, e);
        if (e instanceof OpenAIServiceException serviceException) {
            int status = serviceException.statusCode();
            return new LanguageModelException(status >= 400 ? status : 502, message, e);
        }
        if (e instanceof OpenAIInvalidDataException)
            return new LanguageModelException(502, message, e);
        if (e instanceof OpenAIIoException)
            return new LanguageModelException(isTimeout(e) ? 504 : 503, message, e);
        if (e instanceof IllegalArgumentException)
            return new LanguageModelException(400, message, e);
        return new LanguageModelException(500, message, e);
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable cause = e.getCause(); cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException || cause instanceof TimeoutException) return true;
        }
        return false;
    }

    private static Throwable unwrap(Throwable e) {
        while ((e instanceof CompletionException || e instanceof ExecutionException) && e.getCause() != null)
            e = e.getCause();
        return e;
    }

    // Package-private for testing
    ChatCompletionCreateParams getChatCompletionCreateParams(InferenceParameters parameters, Prompt prompt) {
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
        parameters.get(InferenceParameters.OPTION_REASONING_EFFORT)
                .ifPresent(effort -> builder.reasoningEffort(ReasoningEffort.of(effort)));
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
                
                var jsonSchema = ResponseFormatJsonSchema.JsonSchema.builder()
                        .name("structured-output")
                        .schema(schema);
                if (jsonSchemaStrict) jsonSchema.strict(true);
                var jsonFormat = ResponseFormatJsonSchema.builder()
                    .jsonSchema(jsonSchema.build())
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
            case "error" -> Completion.FinishReason.error;
            default -> Completion.FinishReason.other;
        };
    }
}
