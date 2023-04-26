// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.completion;

import com.yahoo.api.annotations.Beta;

/**
 * A prompt that can be given to a large language model to generate a completion.
 *
 * @author bratseth
 */
@Beta
public abstract class Prompt {

    public abstract String asString();

    /** Returns a new prompt with the text of the given completion appended. */
    public Prompt append(Completion completion) {
        return append(completion.text());
    }

    public abstract Prompt append(String text);

}
