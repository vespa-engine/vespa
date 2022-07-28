// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * @author lesters
 */
public class RankingExpressionWithLightGBMTestCase {

    private final Path applicationDir = Path.fromString("src/test/integration/lightgbm/");

    private final static String lightGBMExpression =
            "if (!(numerical_2 >= 0.46643291586559305), 2.1594397038037663, if (categorical_2 in [\"k\", \"l\", \"m\"], 2.235297305276056, 2.1792953471546546)) + if (categorical_1 in [\"d\", \"e\"], 0.03070842919354316, if (!(numerical_1 >= 0.5102250691730842), -0.04439151147520909, 0.005117411709368601)) + if (!(numerical_2 >= 0.668665477622446), if (!(numerical_2 >= 0.008118820676863816), -0.15361238490967524, -0.01192330846157292), 0.03499044894987518) + if (!(numerical_1 >= 0.5201391072644542), -0.02141000620783247, if (categorical_1 in [\"a\", \"b\"], -0.004121485787596721, 0.04534090904886873)) + if (categorical_2 in [\"k\", \"l\", \"m\"], if (!(numerical_2 >= 0.27283279016959255), -0.01924803254356527, 0.03643772842347651), -0.02701711918923075)";

    @AfterEach
    public void removeGeneratedModelFiles() {
        IOUtils.recursiveDeleteDir(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
    }

    @Test
    void testLightGBMReference() {
        RankProfileSearchFixture search = fixtureWith("lightgbm('regression.json')");
        search.assertFirstPhaseExpression(lightGBMExpression, "my_profile");
    }

    @Test
    void testNestedLightGBMReference() {
        RankProfileSearchFixture search = fixtureWith("5 + sum(lightgbm('regression.json'))");
        search.assertFirstPhaseExpression("5 + reduce(" + lightGBMExpression + ", sum)", "my_profile");
    }

    @Test
    void testImportingFromStoredExpressions() throws IOException {
        RankProfileSearchFixture search = fixtureWith("lightgbm('regression.json')");
        search.assertFirstPhaseExpression(lightGBMExpression, "my_profile");

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedApplicationDirectory = applicationDir.getParentPath().append("copy");
        try {
            storedApplicationDirectory.toFile().mkdirs();
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedApplicationDirectory.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            RankingExpressionWithOnnxTestCase.StoringApplicationPackage storedApplication = new RankingExpressionWithOnnxTestCase.StoringApplicationPackage(storedApplicationDirectory);
            RankProfileSearchFixture searchFromStored = fixtureWith("lightgbm('regression.json')");
            searchFromStored.assertFirstPhaseExpression(lightGBMExpression, "my_profile");
        }
        finally {
            IOUtils.recursiveDeleteDir(storedApplicationDirectory.toFile());
        }
    }

    private RankProfileSearchFixture fixtureWith(String firstPhaseExpression) {
        return fixtureWith(firstPhaseExpression, null, null,
                new RankingExpressionWithOnnxTestCase.StoringApplicationPackage(applicationDir));
    }

    private RankProfileSearchFixture fixtureWith(String firstPhaseExpression,
                                                 String constant,
                                                 String field,
                                                 RankingExpressionWithOnnxTestCase.StoringApplicationPackage application) {
        try {
            RankProfileSearchFixture fixture = new RankProfileSearchFixture(
                    application,
                    application.getQueryProfiles(),
                    "  rank-profile my_profile {\n" +
                            "    first-phase {\n" +
                            "      expression: " + firstPhaseExpression +
                            "    }\n" +
                            "  }",
                    constant,
                    field);
            fixture.compileRankProfile("my_profile", applicationDir.append("models"));
            return fixture;
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

}

