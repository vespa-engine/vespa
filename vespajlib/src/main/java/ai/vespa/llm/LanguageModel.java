// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm;

import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.api.annotations.Beta;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface to language models.
 *
 * @author bratseth
 */
@Beta
public interface LanguageModel {

    List<Completion> complete(Prompt prompt);

    CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt, Consumer<Completion> action);

}
