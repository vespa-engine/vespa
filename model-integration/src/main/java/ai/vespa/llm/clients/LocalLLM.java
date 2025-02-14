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
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
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
    private final long queueTimeoutMilliseconds;

    private final int maxTokens;
    private final int maxPromptTokens;
    private final ContextOverflowPolicy.Enum contextOverflowPolicy;
    private final int contextSizePerRequest;
 
    @Inject
    public LocalLLM(LlmLocalClientConfig config) {
        executor = createExecutor(config);
        queueTimeoutMilliseconds = config.maxQueueWait();

        // Maximum number of tokens to generate - need this since some models can just generate infinitely
        maxTokens = config.maxTokens();

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
            
        long startLoad = System.nanoTime();
        model = new LlamaModel(modelParams);
        long loadTime = System.nanoTime() - startLoad;
        logger.fine(() -> String.format("Loaded model %s in %.2f sec", modelFile, (loadTime*1.0/1000000000)));

        maxPromptTokens = config.maxPromptTokens();
        contextSizePerRequest = config.contextSize() / config.parallelRequests();
        logger.fine(() -> String.format("Context size per request: %d", contextSizePerRequest));
        contextOverflowPolicy = config.contextOverflowPolicy();
    }

    private ThreadPoolExecutor createExecutor(LlmLocalClientConfig config) {
        return new ThreadPoolExecutor(config.parallelRequests(), config.parallelRequests(),
                0L, TimeUnit.MILLISECONDS,
                config.maxQueueSize() > 0 ? new ArrayBlockingQueue<>(config.maxQueueSize()) : new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());
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

        options.ifPresent("temperature", (v) -> inferParams.setTemperature(Float.parseFloat(v)));
        options.ifPresent("topk", (v) -> inferParams.setTopK(Integer.parseInt(v)));
        options.ifPresent("topp", (v) -> inferParams.setTopP(Integer.parseInt(v)));
        options.ifPresent("npredict", (v) -> inferParams.setNPredict(Integer.parseInt(v)));
        options.ifPresent("repeatpenalty", (v) -> inferParams.setRepeatPenalty(Float.parseFloat(v)));
        options.ifPresent("seed", (v) -> inferParams.setSeed(Integer.parseInt(v)));

        inferParams.setUseChatTemplate(true);
        return inferParams;
    } 
    

    @Override
    public List<Completion> complete(Prompt prompt, InferenceParameters options) {
        StringBuilder result = new StringBuilder();

        var future = completeAsync(prompt, options, completion -> result.append(completion.text()));

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
            }
            else if (cause instanceof RejectedExecutionException rejectedExecutionException) {
                throw rejectedExecutionException;
            } else {
                throw new LanguageModelException(500, "Error while generating completion.", cause);
            }
        }
        
        List<Completion> completions = new ArrayList<>();
        completions.add(new Completion(result.toString(), reason));
        return completions;
    }

    @Override
    public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt, InferenceParameters options, Consumer<Completion> consumer) {
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
                case ERROR -> {
                    var errorMessage = String.format(
                            "Context size per request (%d tokens) is too small " +
                                    "to fit the prompt (%d) and completion (%d) tokens.",
                            contextSizePerRequest, promptTokens.length, maxTokens);
                    completionFuture.completeExceptionally(new LanguageModelException(413, errorMessage));
                    return completionFuture;
                }
                case SKIP -> {
                    completionFuture.complete(Completion.FinishReason.skip);
                    return completionFuture;
                }
            }
        }

        var inferenceParams = setInferenceParameters(prompt, options);
        var hasStarted = new AtomicBoolean(false);
        
        try {
            Future<?> future = executor.submit(() -> {
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
            });

            if (queueTimeoutMilliseconds > 0) {
                scheduler.schedule(() -> {
                    if ( ! hasStarted.get()) {
                        future.cancel(false);
                        String error = rejectedExecutionErrorMessage("Rejected completion due to timeout waiting to start");
                        // RejectedExecutionException makes more sense here than LanguageModelException.
                        // Keeping LanguageModelException for backwards compatibility.
                        completionFuture.completeExceptionally(new LanguageModelException(504, error));
                    }
                }, queueTimeoutMilliseconds, TimeUnit.MILLISECONDS);
            }
        } catch (RejectedExecutionException e) {
            // If we have too many requests (active + any waiting in queue), we reject the completion
            var error = rejectedExecutionErrorMessage("Rejected completion due to too many requests");
            completionFuture.completeExceptionally(new RejectedExecutionException(error));
        }
        
        return completionFuture;
    }

    private String rejectedExecutionErrorMessage(String prepend) {
        int activeCount = executor.getActiveCount();
        int queueSize = executor.getQueue().size();
        return String.format("%s, %d active, %d in queue", prepend, activeCount, queueSize);
    }
}
