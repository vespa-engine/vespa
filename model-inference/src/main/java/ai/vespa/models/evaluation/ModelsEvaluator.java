// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.google.common.collect.ImmutableMap;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.tensor.Tensor;
import com.yahoo.vespa.config.search.RankProfilesConfig;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Evaluates machine-learned models added to Vespa applications and available as config form.
 * TODO: Write JavaDoc similar to RankingExpression
 *
 * @author bratseth
 */
public class ModelsEvaluator {

    private final ImmutableMap<String, Model> models;

    public ModelsEvaluator(RankProfilesConfig config) {
        models = ImmutableMap.copyOf(new RankProfilesConfigImporter().importFrom(config));
    }

    /** Returns the models of this as an immutable map */
    public Map<String, Model> models() { return models; }

    /**
     * Returns a function which can be used to evaluate the given function in the given model
     *
     * @throws IllegalArgumentException if the function or model is not present
     */
    public Context contextFor(String modelName, String functionName) {
        return requireModel(modelName).contextFor(functionName);
    }

    /**
     * Evaluates the given function in the given model.
     *
     * @param modelName the name of the model to evaluate
     * @param functionName the function to evaluate in the model
     * @param context the evaluation context which provides bindings of the arguments to the function
     * @return the tensor resulting from evaluating the model, never null
     * @throws IllegalArgumentException if the model of function is not present
     */
    public Tensor evaluate(String modelName, String functionName, Context context) {
        return requireModel(modelName).requireFunction(functionName).getBody().evaluate(context).asTensor();
    }

    /** Returns the given model, or throws a IllegalArgumentException if it does not exist */
    public Model requireModel(String name) {
        Model model = models.get(name);
        if (model == null)
            throw new IllegalArgumentException("No model named '" + name + ". Available models: " +
                                               models.keySet().stream().collect(Collectors.joining(", ")));
        return model;
    }

}
