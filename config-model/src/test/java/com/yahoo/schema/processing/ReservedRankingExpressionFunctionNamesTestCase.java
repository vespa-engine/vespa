// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author lesters
 */
public class ReservedRankingExpressionFunctionNamesTestCase {

    @Test
    void requireThatFunctionsWithReservedNamesIssueAWarning() throws ParseException {
        TestDeployLogger deployLogger = new TestDeployLogger();
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(deployLogger, rankProfileRegistry);
        builder.addSchema(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile test_rank_profile {\n" +
                        "        function not_a_reserved_name(x) {\n" +
                        "            expression: x + x\n" +
                        "        }\n" +
                        "        function sigmoid(x) {\n" +
                        "            expression: x * x\n" +
                        "        }\n" +
                        "        first-phase {\n" +
                        "            expression: sigmoid(2) + not_a_reserved_name(1)\n" +
                        "        }\n" +
                        "    }\n" +
                        "    rank-profile test_rank_profile_2 inherits test_rank_profile {\n" +
                        "        function sin(x) {\n" +
                        "            expression: x * x\n" +
                        "        }\n" +
                        "        first-phase {\n" +
                        "            expression: sigmoid(2) + sin(1)\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");
        builder.build(true);

        assertTrue(deployLogger.log.contains("sigmoid") && deployLogger.log.contains("test_rank_profile"));
        assertTrue(deployLogger.log.contains("sigmoid") && deployLogger.log.contains("test_rank_profile_2"));
        assertTrue(deployLogger.log.contains("sin") && deployLogger.log.contains("test_rank_profile_2"));
        assertFalse(deployLogger.log.contains("not_a_reserved_name") && deployLogger.log.contains("test_rank_profile"));
        assertFalse(deployLogger.log.contains("not_a_reserved_name") && deployLogger.log.contains("test_rank_profile_2"));

    }

    public static class TestDeployLogger implements DeployLogger {
        public String log = "";
        @Override
        public void log(Level level, String message) {
            log += message;
        }
    }
    
}
