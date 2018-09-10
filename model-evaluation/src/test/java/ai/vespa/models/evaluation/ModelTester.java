// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.path.Path;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

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

    private static Map<String, Model> createModels(String path) {
        Path configDir = Path.fromString(path);
        RankProfilesConfig config = new ConfigGetter<>(new FileSource(configDir.append("rank-profiles.cfg").toFile()),
                                                       RankProfilesConfig.class).getConfig("");
        RankingConstantsConfig constantsConfig = new ConfigGetter<>(new FileSource(configDir.append("ranking-constants.cfg").toFile()),
                                                                    RankingConstantsConfig.class).getConfig("");
        return new RankProfilesConfigImporterWithMockedConstants().importFrom(config, constantsConfig);
    }

    public void assertFunction(String name, String expression, Model model) {
        assertNotNull("Model is present in config", model);
        ExpressionFunction function = model.function(name);
        assertNotNull("Function '" + name + "' is in " + model, function);
        assertEquals(name, function.getName());
        assertEquals(expression, function.getBody().getRoot().toString());
    }

    public void assertBoundFunction(String name, String expression, Model model) {
        ExpressionFunction function = model.referencedFunctions().get(FunctionReference.fromSerial(name).get());
        assertNotNull("Function '" + name + "' is present", function);
        assertEquals(name, function.getName());
        assertEquals(expression, function.getBody().getRoot().toString());
    }

    /** Allows us to provide canned tensor constants during import since file distribution does not work in tests */
    private static class RankProfilesConfigImporterWithMockedConstants extends RankProfilesConfigImporter {

        private static final Logger log = Logger.getLogger(RankProfilesConfigImporterWithMockedConstants.class.getName());

        Map<String, Tensor> constants = new HashMap<>();

        @Override
        Tensor readTensorFromFile(String name, TensorType type, String fileReference) {
            if ( ! constants.containsKey(name)) {
                log.warning("Missing a mocked tensor constant for '" + name + "': Returning an empty tensor");
                return Tensor.from(type, "{}");
            }
            return constants.get(name);
        }

    }

}
