// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.client.openai;

import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.api.annotations.Beta;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A client to the OpenAI language model API. Refer to https://platform.openai.com/docs/api-reference/.
 * Currently, only completions are implemented.
 *
 * @author bratseth
 */
@Beta
public class OpenAiClient implements LanguageModel {

    private static final String DATA_FIELD = "data: ";

    private final String token;
    private final String model;
    private final double temperature;
    private final long maxTokens;

    private final HttpClient httpClient;

    private OpenAiClient(Builder builder) {
        this.token = builder.token;
        this.model = builder.model;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public List<Completion> complete(Prompt prompt) {
        try {
            HttpResponse<byte[]> httpResponse = httpClient.send(toRequest(prompt), HttpResponse.BodyHandlers.ofByteArray());
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
    public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt, Consumer<Completion> consumer) {
        try {
            var request = toRequest(prompt, true);
            var futureResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines());
            var completionFuture = new CompletableFuture<Completion.FinishReason>();

            futureResponse.thenAcceptAsync(response -> {
                try {
                    int responseCode = response.statusCode();
                    if (responseCode != 200) {
                        throw new IllegalArgumentException("Received code " + responseCode + ": " +
                                response.body().collect(Collectors.joining()));
                    }

                    Stream<String> lines = response.body();
                    lines.forEach(line -> {
                        if (line.startsWith(DATA_FIELD)) {
                            var root = SlimeUtils.jsonToSlime(line.substring(DATA_FIELD.length())).get();
                            var completion = toCompletions(root, "delta").get(0);
                            consumer.accept(completion);
                            if (!completion.finishReason().equals(Completion.FinishReason.none)) {
                                completionFuture.complete(completion.finishReason());
                            }
                        }
                    });
                } catch (Exception e) {
                    completionFuture.completeExceptionally(e);
                }
            });
            return completionFuture;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpRequest toRequest(Prompt prompt) throws IOException, URISyntaxException {
        return toRequest(prompt, false);
    }

    private HttpRequest toRequest(Prompt prompt, boolean stream) throws IOException, URISyntaxException {
        var slime = new Slime();
        var root = slime.setObject();
        root.setString("model", model);
        root.setDouble("temperature", temperature);
        root.setBool("stream", stream);
        root.setLong("n", 1);
        if (maxTokens > 0) {
            root.setLong("max_tokens", maxTokens);
        }
        var messagesArray = root.setArray("messages");
        var messagesObject = messagesArray.addObject();
        messagesObject.setString("role", "user");
        messagesObject.setString("content", prompt.asString());

        return HttpRequest.newBuilder(new URI("https://api.openai.com/v1/chat/completions"))
                          .header("Content-Type", "application/json")
                          .header("Authorization", "Bearer " + token)
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

    public static class Builder {

        private final String token;
        private String model = "gpt-3.5-turbo";
        private double temperature = 0.0;
        private long maxTokens = 0;

        public Builder(String token) {
            this.token = token;
        }

        /** One of the language models listed at https://platform.openai.com/docs/models */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /** A value between 0 and 2 - higher gives more random/creative output. */
        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        /** Maximum number of tokens to generate */
        public Builder maxTokens(long maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public OpenAiClient build() {
            return new OpenAiClient(this);
        }

    }

}
