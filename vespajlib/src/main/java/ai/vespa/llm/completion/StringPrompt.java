// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.completion;

import com.yahoo.api.annotations.Beta;

import java.util.Objects;

/**
 * A prompt which just consists of a string.
 *
 * @author bratseth
 */
@Beta
public class StringPrompt extends Prompt {

    private final String string;

    private StringPrompt(String string) {
        this.string = Objects.requireNonNull(string);
    }

    @Override
    public String asString() { return string; }

    @Override
    public StringPrompt append(String text) {
        return StringPrompt.from(string + text);
    }

    @Override
    public StringPrompt append(Completion completion) {
        return append(completion.text());
    }

    @Override
    public String toString() {
        return string;
    }

    public static StringPrompt from(String string) {
        return new StringPrompt(string);
    }

}
