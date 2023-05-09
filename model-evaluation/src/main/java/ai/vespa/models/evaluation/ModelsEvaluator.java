// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;

import java.util.Collections;
import java.util.Map;

/**
 * Evaluates machine-learned models added to Vespa applications and available as config form.
 * Usage:
 * <code>Tensor result = evaluator.bind("foo", value).bind("bar", value").evaluate()</code>
 *
 * @author bratseth
 */
@Beta
public class ModelsEvaluator extends AbstractComponent {

    private final Map<String, Model> models;

    @Inject
    public ModelsEvaluator(RankProfilesConfig config,
                           RankingConstantsConfig constantsConfig,
                           RankingExpressionsConfig expressionsConfig,
                           OnnxModelsConfig onnxModelsConfig,
                           FileAcquirer fileAcquirer,
                           OnnxRuntime onnx) {
        this(new RankProfilesConfigImporter(fileAcquirer, onnx), config, constantsConfig, expressionsConfig, onnxModelsConfig);
    }

    public ModelsEvaluator(RankProfilesConfig config,
                           RankingConstantsConfig constantsConfig,
                           RankingExpressionsConfig expressionsConfig,
                           OnnxModelsConfig onnxModelsConfig,
                           FileAcquirer fileAcquirer) {
        this(config, constantsConfig, expressionsConfig, onnxModelsConfig, fileAcquirer, new OnnxRuntime(onnxModelsConfig));
    }

    public ModelsEvaluator(RankProfilesConfigImporter importer,
                           RankProfilesConfig config,
                           RankingConstantsConfig constantsConfig,
                           RankingExpressionsConfig expressionsConfig,
                           OnnxModelsConfig onnxModelsConfig) {
        this(importer.importFrom(config, constantsConfig, expressionsConfig, onnxModelsConfig));
    }

    public ModelsEvaluator(Map<String, Model> models) {
        this.models = Collections.unmodifiableMap(models);
    }

    /** Returns the models of this as an immutable map */
    public Map<String, Model> models() { return models; }

    /**
     * Returns a function which can be used to evaluate the given function in the given model
     *
     * @param modelName the name of the model
     * @param names the 0-2 name components identifying the output to compute
     * @throws IllegalArgumentException if the function or model is not present
     */
    public FunctionEvaluator evaluatorOf(String modelName, String ... names) {
        return requireModel(modelName).evaluatorOf(names);
    }

    /** Returns the given model, or throws a IllegalArgumentException if it does not exist */
    public Model requireModel(String name) {
        Model model = models.get(name);
        if (model == null)
            throw new IllegalArgumentException("No model named '" + name + "'. Available models: " +
                                               String.join(", ", models.keySet()));
        return model;
    }

    @Override public void deconstruct() { models.values().forEach(Model::close); }
}
