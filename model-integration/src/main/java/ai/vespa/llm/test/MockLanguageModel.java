// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.test;

import ai.vespa.llm.Completion;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.Prompt;
import com.yahoo.api.annotations.Beta;

import java.util.List;
import java.util.function.Function;

/**
 * @author bratseth
 */
@Beta
public class MockLanguageModel implements LanguageModel {

    private final Function<Prompt, List<Completion>> completer;

    public MockLanguageModel(Builder builder) {
        completer = builder.completer;
    }

    @Override
    public List<Completion> complete(Prompt prompt) {
        return completer.apply(prompt);
    }

    public static class Builder {

        private Function<Prompt, List<Completion>> completer = prompt -> List.of(Completion.from(""));

        public Builder completer(Function<Prompt, List<Completion>> completer) {
            this.completer = completer;
            return this;
        }

        public Builder() {}

        public MockLanguageModel build() { return new MockLanguageModel(this); }

    }

}
