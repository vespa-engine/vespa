// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.client.openai;

import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Prompt;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;
import com.yahoo.api.annotations.Beta;
import com.yahoo.yolean.Exceptions;

import java.util.List;

/**
 * A client to the OpenAI language model API. Refer to https://platform.openai.com/docs/api-reference/.
 *
 * @author bratseth
 */
@Beta
public class OpenAiClient implements LanguageModel {

    private final OpenAiService openAiService;
    private final String model;
    private final boolean echo;

    private OpenAiClient(Builder builder) {
        openAiService = new OpenAiService(builder.token);
        this.model = builder.model;
        this.echo = builder.echo;
    }

    @Override
    public List<Completion> complete(Prompt prompt) {
        try {
            CompletionRequest completionRequest = CompletionRequest.builder()
                                                          .prompt(prompt.asString())
                                                          .model(model)
                                                          .echo(echo)
                                                          .build();
            return openAiService.createCompletion(completionRequest).getChoices().stream()
                           .map(c -> new Completion(c.getText(), toFinishReason(c.getFinish_reason()))).toList();
        }
        catch (OpenAiHttpException e) {
            throw new RuntimeException(Exceptions.toMessageString(e));
        }
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
        private boolean echo = false;

        public Builder(String token) {
            this.token = token;
        }

        /** One of the language models listed at https://platform.openai.com/docs/models */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder echo(boolean echo) {
            this.echo = echo;
            return this;
        }

        public OpenAiClient build() {
            return new OpenAiClient(this);
        }

    }

}
