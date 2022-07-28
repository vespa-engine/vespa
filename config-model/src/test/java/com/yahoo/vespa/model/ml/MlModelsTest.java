// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.ml;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests rank profile imported model evaluation
 *
 * @author bratseth
 */
public class MlModelsTest {

    @Test
    void testMl_serving() throws IOException {
        Path appDir = Path.fromString("src/test/cfg/application/ml_models");
        Path storedAppDir = appDir.append("copy");
        try {
            ImportedModelTester tester = new ImportedModelTester("ml_models", appDir);
            verify(tester.createVespaModel());

            // At this point the expression is stored - copy application to another location which do not have a models dir
            storedAppDir.toFile().mkdirs();
            IOUtils.copy(appDir.append("services.xml").toString(), storedAppDir.append("services.xml").toString());
            IOUtils.copyDirectory(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedAppDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            IOUtils.copyDirectory(appDir.append(ApplicationPackage.SCHEMAS_DIR).toFile(),
                    storedAppDir.append(ApplicationPackage.SCHEMAS_DIR).toFile());
            ImportedModelTester storedTester = new ImportedModelTester("ml_models", storedAppDir);
            verify(storedTester.createVespaModel());
        }
        finally {
            IOUtils.recursiveDeleteDir(appDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            IOUtils.recursiveDeleteDir(storedAppDir.toFile());
        }
    }

    private void verify(VespaModel model) {
        assertEquals(3, model.rankProfileList().getRankProfiles().size(), "Global models are created (although not used directly here)");

        RankProfilesConfig.Builder builder = new RankProfilesConfig.Builder();
        model.getSearchClusters().get(0).getConfig(builder);
        RankProfilesConfig config = new RankProfilesConfig(builder);
        assertEquals(3, config.rankprofile().size());
        assertEquals("test", config.rankprofile(2).name());
        RankProfilesConfig.Rankprofile.Fef test = config.rankprofile(2).fef();

        // Compare profile content in a denser format than config:
        StringBuilder b = new StringBuilder();
        for (RankProfilesConfig.Rankprofile.Fef.Property p : test.property())
            b.append(p.name()).append(": ").append(p.value()).append("\n");
        assertEquals(testProfile, b.toString());
    }

    private static final String testProfile =
            "rankingExpression(Placeholder).rankingScript: attribute(argument)\n" +
            "rankingExpression(Placeholder).type: tensor<float>(d0[1],d1[784])\n" +
            "rankingExpression(mnist_softmax_onnx).rankingScript: join(reduce(join(rename(rankingExpression(Placeholder), (d0, d1), (d0, d2)), constant(mnist_softmax_Variable), f(a,b)(a * b)), sum, d2), constant(mnist_softmax_Variable_1), f(a,b)(a + b))\n" +
            "rankingExpression(my_xgboost).rankingScript: if (f29 < -0.1234567, if (!(f56 >= -0.242398), 1.71218, -1.70044), if (f109 < 0.8723473, -1.94071, 1.85965)) + if (!(f60 >= -0.482947), if (f29 < -4.2387498, 0.784718, -0.96853), -6.23624)\n" +
            "rankingExpression(my_lightgbm).rankingScript: if (!(numerical_2 >= 0.46643291586559305), 2.1594397038037663, if (categorical_2 in [\"k\", \"l\", \"m\"], 2.235297305276056, 2.1792953471546546)) + if (categorical_1 in [\"d\", \"e\"], 0.03070842919354316, if (!(numerical_1 >= 0.5102250691730842), -0.04439151147520909, 0.005117411709368601)) + if (!(numerical_2 >= 0.668665477622446), if (!(numerical_2 >= 0.008118820676863816), -0.15361238490967524, -0.01192330846157292), 0.03499044894987518) + if (!(numerical_1 >= 0.5201391072644542), -0.02141000620783247, if (categorical_1 in [\"a\", \"b\"], -0.004121485787596721, 0.04534090904886873)) + if (categorical_2 in [\"k\", \"l\", \"m\"], if (!(numerical_2 >= 0.27283279016959255), -0.01924803254356527, 0.03643772842347651), -0.02701711918923075)\n" +
            "rankingExpression(input).rankingScript: attribute(argument)\n" +
            "rankingExpression(input).type: tensor<float>(d0[1],d1[784])\n" +
            "vespa.rank.firstphase: rankingExpression(firstphase)\n" +
            "rankingExpression(firstphase).rankingScript: rankingExpression(mnist_softmax_onnx) + rankingExpression(my_xgboost) + rankingExpression(my_lightgbm)\n" +
            "vespa.type.attribute.argument: tensor<float>(d0[1],d1[784])\n";

}
