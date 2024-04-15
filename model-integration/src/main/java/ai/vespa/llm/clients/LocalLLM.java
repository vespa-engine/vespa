// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.args.LogFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * A language model running locally on the container node.
 *
 * @author lesters
 */
public class LocalLLM extends AbstractComponent implements LanguageModel {

    private final static Logger logger = Logger.getLogger(LocalLLM.class.getName());
    private final LlamaModel model;
    private final ThreadPoolExecutor executor;
    private final int contextSize;
    private final int maxTokens;

    @Inject
    public LocalLLM(LlmLocalClientConfig config) {
        executor = createExecutor(config);

        // Maximum number of tokens to generate - need this since some models can just generate infinitely
        maxTokens = config.maxTokens();

        // Only used if GPU is not used
        var defaultThreadCount = Runtime.getRuntime().availableProcessors() - 2;

        var modelFile = config.model().toFile().getAbsolutePath();
        var modelParams = new ModelParameters()
                .setModelFilePath(modelFile)
                .setContinuousBatching(true)
                .setNParallel(config.parallelRequests())
                .setNThreads(config.threads() <= 0 ? defaultThreadCount : config.threads())
                .setNCtx(config.contextSize())
                .setNGpuLayers(config.useGpu() ? config.gpuLayers() : 0);

        long startLoad = System.nanoTime();
        model = new LlamaModel(modelParams);
        long loadTime = System.nanoTime() - startLoad;
        logger.info(String.format("Loaded model %s in %.2f sec", modelFile, (loadTime*1.0/1000000000)));

        // Todo: handle prompt context size - such as give a warning when prompt exceeds context size
        contextSize = config.contextSize();
    }

    private ThreadPoolExecutor createExecutor(LlmLocalClientConfig config) {
        return new ThreadPoolExecutor(config.parallelRequests(), config.parallelRequests(),
                0L, TimeUnit.MILLISECONDS,
                config.maxQueueSize() > 0 ? new ArrayBlockingQueue<>(config.maxQueueSize()) : new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void deconstruct() {
        logger.info("Closing LLM model...");
        model.close();
        executor.shutdownNow();
    }

    @Override
    public List<Completion> complete(Prompt prompt, InferenceParameters options) {
        StringBuilder result = new StringBuilder();
        var future = completeAsync(prompt, options, completion -> {
            result.append(completion.text());
        }).exceptionally(exception -> Completion.FinishReason.error);
        var reason = future.join();

        List<Completion> completions = new ArrayList<>();
        completions.add(new Completion(result.toString(), reason));
        return completions;
    }

    @Override
    public CompletableFuture<Completion.FinishReason> completeAsync(Prompt prompt, InferenceParameters options, Consumer<Completion> consumer) {
        var inferParams = new de.kherud.llama.InferenceParameters(prompt.asString().stripLeading());

        // We always set this to some value to avoid infinite token generation
        inferParams.setNPredict(maxTokens);

        options.ifPresent("temperature", (v) -> inferParams.setTemperature(Float.parseFloat(v)));
        options.ifPresent("topk", (v) -> inferParams.setTopK(Integer.parseInt(v)));
        options.ifPresent("topp", (v) -> inferParams.setTopP(Integer.parseInt(v)));
        options.ifPresent("npredict", (v) -> inferParams.setNPredict(Integer.parseInt(v)));
        options.ifPresent("repeatpenalty", (v) -> inferParams.setRepeatPenalty(Float.parseFloat(v)));
        // Todo: more options?

        var completionFuture = new CompletableFuture<Completion.FinishReason>();
        try {
            executor.submit(() -> {
                for (LlamaModel.Output output : model.generate(inferParams)) {
                    consumer.accept(Completion.from(output.text, Completion.FinishReason.none));
                }
                completionFuture.complete(Completion.FinishReason.stop);
            });
        } catch (RejectedExecutionException e) {
            // If we have too many requests (active + any waiting in queue), we reject the completion
            int activeCount = executor.getActiveCount();
            int queueSize = executor.getQueue().size();
            String error = String.format("Rejected completion due to too many requests, " +
                    "%d active, %d in queue", activeCount, queueSize);
            throw new RejectedExecutionException(error);
        }
        return completionFuture;
    }

}
