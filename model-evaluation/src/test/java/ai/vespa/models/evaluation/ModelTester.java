// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Helper for testing model import and evaluation
 *
 * @author bratseth
 */
public class ModelTester {

    private final Map<String, Model> models;

    public ModelTester(String modelConfigDirectory) {
        models = createModels(modelConfigDirectory);
    }

    public Map<String, Model> models() { return models; }

    @SuppressWarnings("deprecation")
    private static Map<String, Model> createModels(String path) {
        RankProfilesConfig config = ConfigGetter.getConfig(RankProfilesConfig.class, fileConfigId(path, "rank-profiles.cfg"));
        RankingConstantsConfig constantsConfig = ConfigGetter.getConfig(RankingConstantsConfig.class, fileConfigId(path, "ranking-constants.cfg"));
        RankingExpressionsConfig expressionsConfig = ConfigGetter.getConfig(RankingExpressionsConfig.class, fileConfigId(path, "ranking-expressions.cfg"));
        OnnxModelsConfig onnxModelsConfig = ConfigGetter.getConfig(OnnxModelsConfig.class, fileConfigId(path, "onnx-models.cfg"));

        Map<String, File> fileMap = new HashMap<>();
        for (var cfgEntry : onnxModelsConfig.model()) {
            fileMap.put(cfgEntry.fileref().value(), new File(path + cfgEntry.fileref().value()));
        }
        for (var cfgEntry : constantsConfig.constant()) {
            fileMap.put(cfgEntry.fileref().value(), new File(path + cfgEntry.fileref().value()));
        }
        for (var cfgEntry : expressionsConfig.expression()) {
            fileMap.put(cfgEntry.fileref().value(), new File(path + cfgEntry.fileref().value()));
        }
        var fileAcquirer = MockFileAcquirer.returnFiles(fileMap);

        return new RankProfilesConfigImporter(fileAcquirer, OnnxRuntime.testInstance())
                       .importFrom(config, constantsConfig, expressionsConfig, onnxModelsConfig);
    }

    private static String fileConfigId(String path, String filename) {
        return "file:" + path + filename;
    }

    public ExpressionFunction assertFunction(String name, String expression, Model model) {
        assertNotNull("Model is present in config", model);
        ExpressionFunction function = model.function(name);
        assertNotNull("Function '" + name + "' is in " + model, function);
        assertEquals(name, function.getName());
        assertEquals(expression, function.getBody().getRoot().toString());
        return function;
    }

    public void assertBoundFunction(String name, String expression, Model model) {
        ExpressionFunction function = model.referencedFunctions().get(FunctionReference.fromSerial(name).get());
        assertNotNull("Function '" + name + "' is present", function);
        assertEquals(expression, function.getBody().getRoot().toString());
    }

}
