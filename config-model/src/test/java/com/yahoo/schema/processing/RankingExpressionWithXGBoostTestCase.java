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
 * @author grace-lam
 * @author bratseth
 */
public class RankingExpressionWithXGBoostTestCase {

    private final Path applicationDir = Path.fromString("src/test/integration/xgboost/");

    private final static String vespaExpression =
            "if (f29 < -0.1234567, if (!(f56 >= -0.242398), 1.71218, -1.70044), if (f109 < 0.8723473, -1.94071, 1.85965)) + " +
            "if (!(f60 >= -0.482947), if (f29 < -4.2387498, 0.784718, -0.96853), -6.23624)";

    @AfterEach
    public void removeGeneratedModelFiles() {
        IOUtils.recursiveDeleteDir(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
    }

    @Test
    void testXGBoostReference() {
        RankProfileSearchFixture search = fixtureWith("xgboost('xgboost.2.2.json')");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");
    }

    @Test
    void testNestedXGBoostReference() {
        RankProfileSearchFixture search = fixtureWith("5 + sum(xgboost('xgboost.2.2.json'))");
        search.assertFirstPhaseExpression("5 + reduce(" + vespaExpression + ", sum)", "my_profile");
    }

    @Test
    void testImportingFromStoredExpressions() throws IOException {
        RankProfileSearchFixture search = fixtureWith("xgboost('xgboost.2.2.json')");
        search.assertFirstPhaseExpression(vespaExpression, "my_profile");

        // At this point the expression is stored - copy application to another location which do not have a models dir
        Path storedApplicationDirectory = applicationDir.getParentPath().append("copy");
        try {
            storedApplicationDirectory.toFile().mkdirs();
            IOUtils.copyDirectory(applicationDir.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile(),
                    storedApplicationDirectory.append(ApplicationPackage.MODELS_GENERATED_DIR).toFile());
            RankingExpressionWithOnnxTestCase.StoringApplicationPackage storedApplication = new RankingExpressionWithOnnxTestCase.StoringApplicationPackage(storedApplicationDirectory);
            RankProfileSearchFixture searchFromStored = fixtureWith("xgboost('xgboost.2.2.json')");
            searchFromStored.assertFirstPhaseExpression(vespaExpression, "my_profile");
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

