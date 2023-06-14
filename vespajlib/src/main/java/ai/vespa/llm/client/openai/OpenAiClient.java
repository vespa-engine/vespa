// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

/**
 * A client to the OpenAI language model API. Refer to https://platform.openai.com/docs/api-reference/.
 * Currently only completions are implemented.
 *
 * @author bratseth
 */
@Beta
public class OpenAiClient implements LanguageModel {

    private final String token;
    private final String model;
    private final double temperature;
    private final HttpClient httpClient;

    private OpenAiClient(Builder builder) {
        this.token = builder.token;
        this.model = builder.model;
        this.temperature = builder.temperature;
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

    private HttpRequest toRequest(Prompt prompt) throws IOException, URISyntaxException {
        var slime = new Slime();
        var root = slime.setObject();
        root.setString("model", model);
        root.setDouble("temperature", temperature);
        root.setString("prompt", prompt.asString());
        return HttpRequest.newBuilder(new URI("https://api.openai.com/v1/completions"))
                          .header("Content-Type", "application/json")
                          .header("Authorization", "Bearer " + token)
                          .POST(HttpRequest.BodyPublishers.ofByteArray(SlimeUtils.toJsonBytes(slime)))
                          .build();
    }

    private List<Completion> toCompletions(Inspector response) {
        List<Completion> completions = new ArrayList<>();
        response.field("choices")
                .traverse((ArrayTraverser) (__, choice) -> completions.add(toCompletion(choice)));
        return completions;
    }

    private Completion toCompletion(Inspector choice) {
        return new Completion(choice.field("text").asString(),
                              toFinishReason(choice.field("finish_reason").asString()));
    }

    private Completion.FinishReason toFinishReason(String finishReasonString) {
        return switch(finishReasonString) {
            case "length" -> Completion.FinishReason.length;
            case "stop" -> Completion.FinishReason.stop;
            default -> throw new IllegalStateException("Unknown OpenAi completion finish reason '" + finishReasonString + "'");
        };
    }

    public static class Builder {

        private final String token;
        private String model = "text-davinci-003";
        private double temperature = 0;

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

        public OpenAiClient build() {
            return new OpenAiClient(this);
        }

    }

}
