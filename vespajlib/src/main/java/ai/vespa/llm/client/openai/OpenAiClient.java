// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.client.openai;

import ai.vespa.llm.LanguageModelException;
import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.api.annotations.Beta;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A client to the OpenAI language model API. Refer to https://platform.openai.com/docs/api-reference/.
 * Currently, only completions are implemented.
 *
 * @author bratseth
 * @author lesters
 */
@Beta
public class OpenAiClient implements LanguageModel {

    private static final String DEFAULT_MODEL = "gpt-3.5-turbo";
    private static final String DATA_FIELD = "data: ";

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 250;

    private static final String OPTION_MODEL = "model";
    private static final String OPTION_TEMPERATURE = "temperature";
    private static final String OPTION_MAX_TOKENS = "maxTokens";

    private final HttpClient httpClient;

    public OpenAiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build();
    }

    @Override
    public List<Completion> complete(Prompt prompt, InferenceParameters options) {
        try {
            HttpResponse<byte[]> httpResponse = httpClient.send(toRequest(prompt, options, false), HttpResponse.BodyHandlers.ofByteArray());
            var response = SlimeUtils.jsonToSlime(httpResponse.body()).get();
            if ( httpResponse.statusCode() != 200)
                throw new IllegalArgumentException(SlimeUtils.toJson(response));
            return toCompletions(response);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt,
                                                                    InferenceParameters options,
                                                                    Consumer<Completion> consumer) {
        var completionContext = new CompletionContext(prompt, options, consumer);
        completeAsyncAttempt(completionContext, 0);
        return completionContext.completionFuture();
    }

    private record CompletionContext(Prompt prompt,
                                     InferenceParameters options,
                                     Consumer<Completion> consumer,
                                     CompletableFuture<Completion.FinishReason> completionFuture) {
        CompletionContext(Prompt prompt, InferenceParameters options, Consumer<Completion> consumer) {
            this(prompt, options, consumer, new CompletableFuture<>());
        }
    }

    private void completeAsyncAttempt(CompletionContext context, int attempt) {
        try {
            var request = toRequest(context.prompt(), context.options(), true);
            var futureResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                                           .orTimeout(10, TimeUnit.SECONDS);  // timeout for start of response

            futureResponse.thenAccept(response -> {
                handleHttpResponse(response, context);
            }).exceptionally(exception -> {
                handleHttpException(exception, context, attempt);
                return null;
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleHttpResponse(HttpResponse<Stream<String>> response, CompletionContext context) {
        try {
            int responseCode = response.statusCode();
            if (responseCode != 200) {
                throw new LanguageModelException(responseCode, response.body().collect(Collectors.joining()));
            }
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> processLine(context, line));
            }
        } catch (Exception e) {
            context.completionFuture().completeExceptionally(e);
        }
    }

    private void processLine(CompletionContext context, String line) {
        if (line.startsWith(DATA_FIELD)) {
            var root = SlimeUtils.jsonToSlime(line.substring(DATA_FIELD.length())).get();
            var completion = toCompletions(root, "delta").get(0);
            context.consumer().accept(completion);
            if (!completion.finishReason().equals(Completion.FinishReason.none)) {
                context.completionFuture().complete(completion.finishReason());
            }
        }
    }

    private void processLines(CompletionContext context, Stream<String> lines) {
    }

    private void waitBeforeRetry() {
        try {
            TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldRetry(Throwable exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof SocketException && cause.getMessage().contains("Connection reset")) {
            return true;
        }
        if (cause instanceof HttpConnectTimeoutException) {
            return true;
        }
        if (cause instanceof HttpTimeoutException) {
            return true;
        }
        return false;
    }

    private void handleHttpException(Throwable exception, CompletionContext context, int attempt) {
        if (shouldRetry(exception)) {
            if (attempt < MAX_RETRIES) {
                waitBeforeRetry();
                completeAsyncAttempt(context, attempt + 1);
            } else {
                context.completionFuture().completeExceptionally(new RuntimeException("OpenAI: max retries reached"));
            }
        } else {
            context.completionFuture().completeExceptionally(exception);
        }
    }

    private HttpRequest toRequest(Prompt prompt, InferenceParameters options, boolean stream) throws IOException, URISyntaxException {
        var slime = new Slime();
        var root = slime.setObject();
        root.setString("model", options.get(OPTION_MODEL).orElse(DEFAULT_MODEL));
        root.setBool("stream", stream);
        root.setLong("n", 1);

        if (options.getDouble(OPTION_TEMPERATURE).isPresent())
            root.setDouble("temperature", options.getDouble(OPTION_TEMPERATURE).get());
        if (options.getInt(OPTION_MAX_TOKENS).isPresent())
            root.setLong("max_tokens", options.getInt(OPTION_MAX_TOKENS).get());
        // Others?

        var messagesArray = root.setArray("messages");
        var messagesObject = messagesArray.addObject();
        messagesObject.setString("role", "user");
        messagesObject.setString("content", prompt.asString());

        var endpoint = options.getEndpoint().orElse("https://api.openai.com/v1/chat/completions");
        return HttpRequest.newBuilder(new URI(endpoint))
                          .header("Content-Type", "application/json")
                          .header("Authorization", "Bearer " + options.getApiKey().orElse(""))
                          .POST(HttpRequest.BodyPublishers.ofByteArray(SlimeUtils.toJsonBytes(slime)))
                          .build();
    }

    private List<Completion> toCompletions(Inspector response) {
        return toCompletions(response, "message");
    }

    private List<Completion> toCompletions(Inspector response, String field) {
        List<Completion> completions = new ArrayList<>();
        response.field("choices")
                .traverse((ArrayTraverser) (__, choice) -> completions.add(toCompletion(choice, field)));
        return completions;
    }

    private Completion toCompletion(Inspector choice, String field) {
        var content = choice.field(field).field("content").asString();
        var finishReason = toFinishReason(choice.field("finish_reason").asString());
        return new Completion(content, finishReason);
    }

    private Completion.FinishReason toFinishReason(String finishReasonString) {
        return switch(finishReasonString) {
            case "length" -> Completion.FinishReason.length;
            case "stop" -> Completion.FinishReason.stop;
            case "", "null" -> Completion.FinishReason.none;
            default -> throw new IllegalStateException("Unknown OpenAi completion finish reason '" + finishReasonString + "'");
        };
    }

}
