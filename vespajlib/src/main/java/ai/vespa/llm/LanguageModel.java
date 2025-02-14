// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm;

import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.api.annotations.Beta;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Interface to language models.
 *
 * @author bratseth
 */
@Beta
public interface LanguageModel {
    /**
     * @throws RejectedExecutionException – if the completion task cannot be scheduled for execution or timeouts
     * @throws LanguageModelException – if the completion task fails
     */
    List<Completion> complete(Prompt prompt, InferenceParameters options);

    CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt,
                                                             InferenceParameters options,
                                                             Consumer<Completion> consumer);
}
