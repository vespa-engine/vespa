// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation.config;

import ai.vespa.models.evaluation.Model;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests instantiating models from rank-profiles configs.
 *
 * @author bratseth
 */
public class RankProfilesImporterTest {

    @Test
    public void testRankexpression() {
        String configPath = "src/test/resources/config/rankexpression/rank-profiles.cfg";
        RankProfilesConfig config = new ConfigGetter<>(new FileSource(new File(configPath)), RankProfilesConfig.class).getConfig("");
        Map<String, Model> models = new RankProfilesConfigImporter().importFrom(config);
        assertEquals(18, models.size());
        Model macros = models.get("macros");
        assertNotNull(macros);
        assertEquals(4, macros.functions().size());
        ExpressionFunction function = functionByName("fourtimessum", macros);
        assertNotNull(function);

    }

    @Test
    public void testRegexp() {
        assertTrue("a(foo)".matches("a\\([a-zA-Z0-9_]+\\)"));
    }

    private ExpressionFunction functionByName(String name, Model model) {
        for (ExpressionFunction function : model.functions())
            if (function.getName().equals(name))
                return function;
        return null;
    }

}
