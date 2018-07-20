// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests instantiating models from rank-profiles configs.
 *
 * @author bratseth
 */
public class RankProfilesImporterTest {

    @Test
    public void testImporting() {
        String configPath = "src/test/resources/config/rankexpression/rank-profiles.cfg";
        RankProfilesConfig config = new ConfigGetter<>(new FileSource(new File(configPath)), RankProfilesConfig.class).getConfig("");
        Map<String, Model> models = new RankProfilesConfigImporter().importFrom(config);
        assertEquals(18, models.size());

        Model macros = models.get("macros");
        assertNotNull(macros);
        assertEquals("macros", macros.name());
        assertEquals(4, macros.functions().size());
        assertFunction("fourtimessum", "4 * (var1 + var2)", macros);
        assertFunction("firstphase", "match + fieldMatch(title) + rankingExpression(myfeature)", macros);
        assertFunction("secondphase", "rankingExpression(fourtimessum@5cf279212355b980.67f1e87166cfef86)", macros);
        assertFunction("myfeature",
                       "70 * fieldMatch(title).completeness * pow(0 - fieldMatch(title).earliness,2) + " +
                       "30 * pow(0 - fieldMatch(description).earliness,2)",
                       macros);
        assertEquals(1, macros.boundFunctions().size());
        assertBoundFunction("rankingExpression(fourtimessum@5cf279212355b980.67f1e87166cfef86)",
                            "4 * (match + rankBoost)", macros);
    }

    private void assertFunction(String name, String expression, Model model) {
        ExpressionFunction function = model.function(name);
        assertNotNull(function);
        assertEquals(name, function.getName());
        assertEquals(expression, function.getBody().getRoot().toString());
    }

    private void assertBoundFunction(String name, String expression, Model model) {
        ExpressionFunction function = model.boundFunctions().get(name);
        assertNotNull("Function '" + name + "' is present", function);
        assertEquals(name, function.getName());
        assertEquals(expression, function.getBody().getRoot().toString());
    }

}
