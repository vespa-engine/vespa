// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import ai.vespa.modelintegration.evaluator.OnnxRuntime;
import com.yahoo.config.FileReference;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests importing rank profiles whose expressions are distributed as files.
 */
public class LargeExpressionImportingTest {

    private static final String CONFIG_DIR = "src/test/resources/config/rankexpression/";

    @SuppressWarnings("deprecation")
    private final RankProfilesConfig profiles = ConfigGetter.getConfig(RankProfilesConfig.class, "file:" + CONFIG_DIR + "rank-profiles.cfg");
    @SuppressWarnings("deprecation")
    private final RankingConstantsConfig constants = ConfigGetter.getConfig(RankingConstantsConfig.class, "file:" + CONFIG_DIR + "ranking-constants.cfg");
    @SuppressWarnings("deprecation")
    private final RankingExpressionsConfig expressions = ConfigGetter.getConfig(RankingExpressionsConfig.class, "file:" + CONFIG_DIR + "ranking-expressions.cfg");
    @SuppressWarnings("deprecation")
    private final OnnxModelsConfig onnxModels = ConfigGetter.getConfig(OnnxModelsConfig.class, "file:" + CONFIG_DIR + "onnx-models.cfg");

    @Test
    public void testEachLargeExpressionIsParsedOnce() {
        var importer = new CountingImporter(expressionFiles());
        var models = importer.importFrom(profiles, constants, expressions, onnxModels);

        // Only 'overflow' uses large expressions; parsing them for every profile would read each 18 times
        assertEquals(18, models.size());
        assertEquals(Map.of("overflow.firstphase", 1, "overflow.secondphase", 1), importer.parsed);
    }

    @Test
    public void testMissingLargeExpressionIsReported() {
        var noExpressions = new RankingExpressionsConfig(new RankingExpressionsConfig.Builder());
        var e = assertThrows(IllegalArgumentException.class,
                             () -> new CountingImporter(expressionFiles()).importFrom(profiles, constants, noExpressions, onnxModels));
        assertTrue(e.getMessage(), e.getMessage().contains("'overflow'"));
        assertTrue(e.getMessage(), e.getMessage().contains("'overflow.firstphase'"));
    }

    private static class CountingImporter extends RankProfilesConfigImporter {

        final Map<String, Integer> parsed = new HashMap<>();

        CountingImporter(Map<String, File> files) {
            super(MockFileAcquirer.returnFiles(files), OnnxRuntime.testInstance());
        }

        @Override
        protected RankingExpression readExpressionFromFile(String name, FileReference fileReference) throws ParseException {
            parsed.merge(name, 1, Integer::sum);
            return super.readExpressionFromFile(name, fileReference);
        }

    }

    private Map<String, File> expressionFiles() {
        Map<String, File> files = new HashMap<>();
        for (var expression : expressions.expression())
            files.put(expression.fileref().value(), new File(CONFIG_DIR + expression.fileref().value()));
        return files;
    }

}
