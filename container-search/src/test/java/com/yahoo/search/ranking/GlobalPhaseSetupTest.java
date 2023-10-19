// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.tensor.Tensor;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GlobalPhaseSetupTest {
    private static final String CONFIG_DIR = "src/test/resources/config/";

    @SuppressWarnings("deprecation")
    RankProfilesConfig readConfig(String subDir) {
        String cfgId = "file:" + CONFIG_DIR + subDir + "/rank-profiles.cfg";
        return ConfigGetter.getConfig(RankProfilesConfig.class, cfgId);
    }

    @Test void mediumAdvancedSetup() {
        RankProfilesConfig rpCfg = readConfig("medium");
        assertEquals(1, rpCfg.rankprofile().size());
        RankProfilesEvaluator rpEvaluator = createEvaluator(rpCfg);
        var setup = GlobalPhaseSetup.maybeMakeSetup(rpCfg.rankprofile().get(0), rpEvaluator);
        assertNotNull(setup);
        assertEquals(42, setup.rerankCount);
        assertEquals(0, setup.normalizers.size());
        assertEquals(9, setup.matchFeaturesToHide.size());
        assertEquals(1, setup.globalPhaseEvalSpec.fromQuery().size());
        assertEquals(9, setup.globalPhaseEvalSpec.fromMF().size());
    }

    @Test void queryFeaturesWithDefaults() {
        RankProfilesConfig rpCfg = readConfig("qf_defaults");
        assertEquals(1, rpCfg.rankprofile().size());
        RankProfilesEvaluator rpEvaluator = createEvaluator(rpCfg);
        var setup = GlobalPhaseSetup.maybeMakeSetup(rpCfg.rankprofile().get(0), rpEvaluator);
        assertNotNull(setup);
        assertEquals(0, setup.normalizers.size());
        assertEquals(0, setup.matchFeaturesToHide.size());
        assertEquals(5, setup.globalPhaseEvalSpec.fromQuery().size());
        assertEquals(2, setup.globalPhaseEvalSpec.fromMF().size());
        assertEquals(5, setup.defaultValues.size());
        assertEquals(Tensor.from(0.0), setup.defaultValues.get("query(w_no_def)"));
        assertEquals(Tensor.from(1.0), setup.defaultValues.get("query(w_has_def)"));
        assertEquals(Tensor.from("tensor(m{}):{}"), setup.defaultValues.get("query(m_no_def)"));
        assertEquals(Tensor.from("tensor(v[3]):[0,0,0]"), setup.defaultValues.get("query(v_no_def)"));
        assertEquals(Tensor.from("tensor(v[3]):[2,0.25,1.5]"), setup.defaultValues.get("query(v_has_def)"));
    }

    private RankProfilesEvaluator createEvaluator(RankProfilesConfig config) {
        RankingConstantsConfig constantsConfig = new RankingConstantsConfig.Builder().build();
        RankingExpressionsConfig expressionsConfig = new RankingExpressionsConfig.Builder().build();
        OnnxModelsConfig onnxModelsConfig = new OnnxModelsConfig.Builder().build();
        return new RankProfilesEvaluator(config, constantsConfig, expressionsConfig, onnxModelsConfig, MockFileAcquirer.returnFile(null));
    }
}
