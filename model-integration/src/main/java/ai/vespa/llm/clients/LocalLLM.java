// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.LanguageModelException;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.llm.completion.StringPrompt;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

import ai.vespa.llm.clients.LlmLocalClientConfig.ContextOverflowPolicy;

/**
 * A language model running locally on the container node.
 *
 * @author lesters
 * @author glebashnik
 */
public class LocalLLM extends AbstractComponent implements LanguageModel {

    private final static Logger logger = Logger.getLogger(LocalLLM.class.getName());

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final LlamaModel model;
    private final ThreadPoolExecutor executor;
    private final long maxQueueWait;
    private final long maxEnqueueWait;

    private final int maxTokens;
    private final int maxPromptTokens;
    private final ContextOverflowPolicy.Enum contextOverflowPolicy;
    private final int contextSizePerRequest;
 
    @Inject
    public LocalLLM(LlmLocalClientConfig config) {
        
        // Only used if GPU is not used
        var defaultThreadCount = Math.max(Runtime.getRuntime().availableProcessors() - 2, 1);
        var modelFile = config.model().toFile().getAbsolutePath();
        var modelParams = new ModelParameters()
                .setModelFilePath(modelFile)
                .setContinuousBatching(true)
                .setNParallel(config.parallelRequests())
                .setNThreads(config.threads() <= 0 ? defaultThreadCount : config.threads())
                .setNCtx(config.contextSize())
                .setNGpuLayers(config.useGpu() ? config.gpuLayers() : 0);
        
        if (config.seed() != -1)
            modelParams.setSeed(config.seed());    
        
        // Load model
        long startLoad = System.nanoTime();
        model = new LlamaModel(modelParams);
        long loadTime = System.nanoTime() - startLoad;
        logger.fine(() -> String.format("Loaded model %s in %.2f sec", modelFile, (loadTime*1.0/1000000000)));

        // Executor for continuously batching requests that are fed to LLM to maximizing compute utilization.
        executor = new ThreadPoolExecutor(
                config.parallelRequests(),
                config.parallelRequests(),
                0L, TimeUnit.MILLISECONDS,
                config.maxQueueSize() > 0 ? new ArrayBlockingQueue<>(config.maxQueueSize()) : new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        
        // Staring all threads manually because we are using `executor.getQueue().offer(...)` to add tasks 
        // instead of higher-level methods like `executor.submit(...)` that automatically start threads when needed.
        executor.prestartAllCoreThreads();
        
        // Setting other config parameters
        maxQueueWait = config.maxQueueWait();
        maxEnqueueWait = config.maxEnqueueWait();
        maxTokens = config.maxTokens();
        maxPromptTokens = config.maxPromptTokens();
        contextSizePerRequest = config.contextSize() / config.parallelRequests();
        logger.fine(() -> String.format("Context size per request: %d", contextSizePerRequest));
        contextOverflowPolicy = config.contextOverflowPolicy();
    }
    
    @Override
    public void deconstruct() {
        model.close();
        executor.shutdownNow();
        scheduler.shutdownNow();
    }
    
    private de.kherud.llama.InferenceParameters setInferenceParameters(Prompt prompt, InferenceParameters options) {
        var inferParams = new de.kherud.llama.InferenceParameters(prompt.asString().stripLeading());

        // We always set this to some value to avoid infinite token generation
        inferParams.setNPredict(maxTokens);

        options.ifPresent(InferenceParameters.OPTION_TEMPERATURE, (v) -> inferParams.setTemperature(Float.parseFloat(v)));
        options.ifPresent(InferenceParameters.OPTION_TOP_K, (v) -> inferParams.setTopK(Integer.parseInt(v)));
        options.ifPresent(InferenceParameters.OPTION_TOP_P, (v) -> inferParams.setTopP(Integer.parseInt(v)));
        options.ifPresent(InferenceParameters.OPTION_N_PREDICT, (v) -> inferParams.setNPredict(Integer.parseInt(v)));
        options.ifPresent(InferenceParameters.OPTION_REPEAT_PENALTY, (v) -> inferParams.setRepeatPenalty(Float.parseFloat(v)));
        options.ifPresent(InferenceParameters.OPTION_SEED, (v) -> inferParams.setSeed(Integer.parseInt(v)));
        options.ifPresent(InferenceParameters.OPTION_JSON_SCHEMA, (v) -> {
            var grammar = JsonSchemaToGrammar.convert(v);
            inferParams.setGrammar(grammar);
        });
        
        inferParams.setUseChatTemplate(true);
        return inferParams;
    } 
    

    @Override
    public List<Completion> complete(Prompt prompt, InferenceParameters options) {
        StringBuilder result = new StringBuilder();
        var future = completeWithOffer(prompt, options, 
                completion -> result.append(completion.text()), maxEnqueueWait);
        Completion.FinishReason reason;
    
        try {
            reason = future.get();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new LanguageModelException(500, "Interruption while generating completion.");
        } catch (ExecutionException e) {
            var cause = e.getCause();

            if (cause instanceof LanguageModelException languageModelException) {
                throw languageModelException;
            } else {
                throw new LanguageModelException(500, "Error while generating completion.", cause);
            }
        }
        
        List<Completion> completions = new ArrayList<>();
        completions.add(new Completion(result.toString(), reason));
        return completions;
    }

    public CompletableFuture<Completion.FinishReason> completeAsync(
            Prompt prompt, InferenceParameters options, Consumer<Completion> consumer) {
        return completeWithOffer(prompt, options, consumer, 0);
    }
    
    /**
     * Completes the given prompt, mostly asynchronously with or without a blocking wait.
     * It is used by both `complete()` and `completeAsync()` with different `offerTimeout` values.
     * When set to 0, there is no blocking wait, the request is either added to the queue or rejected immediately.
     * When larger than 0 and the queue is full, there will be a blocking wait up to `offerTimeout` milliseconds.
     * This blocking is used for throttling the incoming requests, propagating the delay up the stack.
     */
    private CompletableFuture<Completion.FinishReason> completeWithOffer(
            Prompt prompt, InferenceParameters options, Consumer<Completion> consumer, long offerTimeout) {
        var completionFuture = new CompletableFuture<Completion.FinishReason>();
        
        var promptStr = prompt.asString().stripLeading();
        var promptTokens = model.encode(promptStr);
        
        // Truncate prompt
        if (maxPromptTokens > 0 && promptTokens.length > maxPromptTokens) {
            promptTokens = Arrays.copyOfRange(promptTokens, 0, maxPromptTokens + 1);
            promptStr = model.decode(promptTokens);
            prompt = StringPrompt.from(promptStr);
        }
        
        var numPromptTokens = promptTokens.length;
        var numRequestTokens = numPromptTokens + maxTokens;
        logger.fine(() -> String.format("Prompt tokens: %d, max tokens: %d, request tokens: %d", 
                numPromptTokens, maxTokens, numRequestTokens));

        // Do something when context size is too small for this request
        if (numRequestTokens > contextSizePerRequest) {
            switch (contextOverflowPolicy) {
                case ABORT:
                    var errorMessage = String.format(
                            "Context size per request (%d tokens) is too small " +
                                    "to fit the prompt (%d) and completion (%d) tokens.",
                            contextSizePerRequest, promptTokens.length, maxTokens);
                    completionFuture.completeExceptionally(new LanguageModelException(413, errorMessage));
                    return completionFuture;
                case DISCARD:
                    completionFuture.complete(Completion.FinishReason.discard);
                    return completionFuture;
                case NONE:
                    break;
            }
        }

        var inferenceParams = setInferenceParameters(prompt, options);
        var hasStarted = new AtomicBoolean(false);
        var future = new FutureTask<>(() -> {
            hasStarted.set(true);

            try {
                // Actual generation, stream outputs
                for (var output : model.generate(inferenceParams)) {
                    consumer.accept(Completion.from(output.text, Completion.FinishReason.none));
                }
                completionFuture.complete(Completion.FinishReason.stop);
            } catch (Exception e) {
                var errorMessage = "Error while generating completion in executor thread.";
                completionFuture.completeExceptionally(new LanguageModelException(500, errorMessage, e));
            }
        }, null);
        
        try {
            var accepted = offerTimeout > 0 
                    ? executor.getQueue().offer(future, offerTimeout, TimeUnit.MILLISECONDS) 
                    : executor.getQueue().offer(future);
                
            if (!accepted) {
                String errorMessage = rejectedExecutionErrorMessage(
                        "Rejected completion due to timeout waiting to add the request to the executor queue");
                completionFuture.completeExceptionally(new LanguageModelException(504, errorMessage));
                return completionFuture;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMessage = rejectedExecutionErrorMessage(
                    "Rejected completion due to interruption when adding the request to the executor queue");
            completionFuture.completeExceptionally(new LanguageModelException(500, errorMessage));
            return completionFuture;
        }
        
        if (maxQueueWait > 0) {
            scheduler.schedule(
                    () -> {
                        if (!hasStarted.get()) {
                            future.cancel(false);
                            executor.remove(future);
                            String errorMessage = rejectedExecutionErrorMessage(
                                    "Rejected completion due to timeout waiting to start processing the request");
                            completionFuture.completeExceptionally(new LanguageModelException(504, errorMessage));
                        }
                    }, maxQueueWait, TimeUnit.MILLISECONDS
            );
        }
        
        return completionFuture;
    }

    private String rejectedExecutionErrorMessage(String prefix) {
        int activeCount = executor.getActiveCount();
        int queueSize = executor.getQueue().size();
        return String.format("%s, %d active, %d in queue", prefix, activeCount, queueSize);
    }
}
