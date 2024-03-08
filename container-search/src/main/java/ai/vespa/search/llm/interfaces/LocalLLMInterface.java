package ai.vespa.search.llm.interfaces;

import ai.vespa.llm.InferenceParameters;
import ai.vespa.llm.LanguageModel;
import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.Prompt;
import ai.vespa.search.llm.LocalLlmInterfaceConfig;

import ai.vespa.util.http.hc4.retry.Sleeper;
import com.yahoo.component.annotation.Inject;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LogLevel;
import de.kherud.llama.ModelParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class LocalLLMInterface implements LanguageModel {

    private static Logger logger = Logger.getLogger(LocalLLMInterface.class.getName());
    private final LlamaModel model;
    private final ExecutorService executor;

    @Inject
    public LocalLLMInterface(LocalLlmInterfaceConfig config) {
        this(config, Executors.newFixedThreadPool(1));  // until we can run llama.cpp in batch
    }

    LocalLLMInterface(LocalLlmInterfaceConfig config, ExecutorService executor) {
        this.executor = executor;

        LlamaModel.setLogger(this::log);
        var modelParams = new ModelParameters()
                // Todo: retrieve from config
                ;

        long startLoad = System.nanoTime();
        model = new LlamaModel(config.llmfile(), modelParams);
        long loadTime = System.nanoTime() - startLoad;
        logger.info("Loaded model " + config.llmfile() + " in " + (loadTime*1.0/1000000000) + "  sec");
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
        var inferParams = new de.kherud.llama.InferenceParameters();
        options.ifPresent("temperature", (v) -> inferParams.setTemperature(Float.parseFloat(v)));
        options.ifPresent("topk", (v) -> inferParams.setTopK(Integer.parseInt(v)));
        options.ifPresent("topp", (v) -> inferParams.setTopP(Integer.parseInt(v)));
        options.ifPresent("npredict", (v) -> inferParams.setNPredict(Integer.parseInt(v)));
        options.ifPresent("repeatpenalty", (v) -> inferParams.setRepeatPenalty(Float.parseFloat(v)));
        // Todo: add more

        var completionFuture = new CompletableFuture<Completion.FinishReason>();
        executor.submit(() -> {
            for (LlamaModel.Output output : model.generate(prompt.asString(), inferParams)) {
                consumer.accept(Completion.from(output.text, Completion.FinishReason.none));
            }
            completionFuture.complete(Completion.FinishReason.stop);
        });

        return completionFuture;
    }

    private void log(LogLevel level, String message) {
        switch (level) {
            case WARN -> logger.warning(message);
            case DEBUG -> logger.fine(message);
            case ERROR -> logger.severe(message);
            default -> logger.info(message);
        }
    }

}
