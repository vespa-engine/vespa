// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.completion;

import com.yahoo.api.annotations.Beta;

import java.util.Objects;

/**
 * A completion from a language model.
 *
 * @author bratseth
 */
@Beta
public record Completion(String text, FinishReason finishReason) {

    public enum FinishReason {

        /** The maximum length of a completion was reached. */
        length,

        /** The completion is the predicted ending of the prompt. */
        stop

    }

    public Completion(String text, FinishReason finishReason) {
        this.text = Objects.requireNonNull(text);
        this.finishReason = Objects.requireNonNull(finishReason);
    }

    /** Returns the generated text completion. */
    public String text() { return text; }

    /** Returns the reason this completion ended. */
    public FinishReason finishReason() { return finishReason; }

    public static Completion from(String text) {
        return new Completion(text, FinishReason.stop);
    }

}
