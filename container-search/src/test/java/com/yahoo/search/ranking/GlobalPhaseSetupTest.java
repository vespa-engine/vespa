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
        var wantMF = setup.globalPhaseEvalSpec.fromMF();
        assertEquals(8, wantMF.size());
        wantMF.sort((a, b) -> a.matchFeatureName().compareTo(b.matchFeatureName()));
        assertEquals("attribute(t1)", wantMF.get(0).matchFeatureName());
        assertEquals("attribute(t1)", wantMF.get(0).inputName());
        assertEquals("myplus", wantMF.get(5).matchFeatureName());
        assertEquals("rankingExpression(myplus)", wantMF.get(5).inputName());
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

    @Test void withNormalizers() {
        RankProfilesConfig rpCfg = readConfig("with_normalizers");
        assertEquals(1, rpCfg.rankprofile().size());
        RankProfilesEvaluator rpEvaluator = createEvaluator(rpCfg);
        var setup = GlobalPhaseSetup.maybeMakeSetup(rpCfg.rankprofile().get(0), rpEvaluator);
        assertNotNull(setup);
        var nList = setup.normalizers;
        assertEquals(7, nList.size());
        nList.sort((a,b) -> a.name().compareTo(b.name()));

        var n = nList.get(0);
        assertEquals("normalize@2974853441@linear", n.name());
        assertEquals(0, n.inputEvalSpec().fromQuery().size());
        assertEquals(1, n.inputEvalSpec().fromMF().size());
        assertEquals("funmf", n.inputEvalSpec().fromMF().get(0).matchFeatureName());
        assertEquals("linear", n.supplier().get().normalizing());

        n = nList.get(1);
        assertEquals("normalize@3414032797@rrank", n.name());
        assertEquals(0, n.inputEvalSpec().fromQuery().size());
        assertEquals(1, n.inputEvalSpec().fromMF().size());
        assertEquals("attribute(year)", n.inputEvalSpec().fromMF().get(0).inputName());
        assertEquals("reciprocal-rank{k:60.0}", n.supplier().get().normalizing());

        n = nList.get(2);
        assertEquals("normalize@3551296680@linear", n.name());
        assertEquals(0, n.inputEvalSpec().fromQuery().size());
        assertEquals(1, n.inputEvalSpec().fromMF().size());
        assertEquals("nativeRank", n.inputEvalSpec().fromMF().get(0).inputName());
        assertEquals("linear", n.supplier().get().normalizing());

        n = nList.get(3);
        assertEquals("normalize@4280591309@rrank", n.name());
        assertEquals(0, n.inputEvalSpec().fromQuery().size());
        assertEquals(1, n.inputEvalSpec().fromMF().size());
        assertEquals("bm25(myabstract)", n.inputEvalSpec().fromMF().get(0).inputName());
        assertEquals("reciprocal-rank{k:42.0}", n.supplier().get().normalizing());

        n = nList.get(4);
        assertEquals("normalize@4370385022@linear", n.name());
        assertEquals(1, n.inputEvalSpec().fromQuery().size());
        assertEquals("myweight", n.inputEvalSpec().fromQuery().get(0));
        assertEquals(1, n.inputEvalSpec().fromMF().size());
        assertEquals("attribute(foo1)", n.inputEvalSpec().fromMF().get(0).inputName());
        assertEquals("linear", n.supplier().get().normalizing());

        n = nList.get(5);
        assertEquals("normalize@4640646880@linear", n.name());
        assertEquals(0, n.inputEvalSpec().fromQuery().size());
        assertEquals(1, n.inputEvalSpec().fromMF().size());
        assertEquals("attribute(foo1)", n.inputEvalSpec().fromMF().get(0).inputName());
        assertEquals("linear", n.supplier().get().normalizing());

        n = nList.get(6);
        assertEquals("normalize@6283155534@linear", n.name());
        assertEquals(0, n.inputEvalSpec().fromQuery().size());
        assertEquals(1, n.inputEvalSpec().fromMF().size());
        assertEquals("bm25(mytitle)", n.inputEvalSpec().fromMF().get(0).inputName());
        assertEquals("linear", n.supplier().get().normalizing());
    }

    @Test void funcWithArgsSetup() {
        RankProfilesConfig rpCfg = readConfig("with_mf_funargs");
        assertEquals(1, rpCfg.rankprofile().size());
        RankProfilesEvaluator rpEvaluator = createEvaluator(rpCfg);
        var setup = GlobalPhaseSetup.maybeMakeSetup(rpCfg.rankprofile().get(0), rpEvaluator);
        assertNotNull(setup);
        assertEquals(0, setup.normalizers.size());
        assertEquals(3, setup.matchFeaturesToHide.size());
        assertEquals(0, setup.globalPhaseEvalSpec.fromQuery().size());
        var wantMF = setup.globalPhaseEvalSpec.fromMF();
        assertEquals(4, wantMF.size());
        wantMF.sort((a, b) -> a.matchFeatureName().compareTo(b.matchFeatureName()));
        assertEquals("plusOne(2)", wantMF.get(0).matchFeatureName());
        assertEquals("plusOne(attribute(foo2))", wantMF.get(1).matchFeatureName());
        assertEquals("useAttr(t1,42)", wantMF.get(2).matchFeatureName());
        assertEquals("withIndirect(foo1)", wantMF.get(3).matchFeatureName());
    }

    private RankProfilesEvaluator createEvaluator(RankProfilesConfig config) {
        RankingConstantsConfig constantsConfig = new RankingConstantsConfig.Builder().build();
        RankingExpressionsConfig expressionsConfig = new RankingExpressionsConfig.Builder().build();
        OnnxModelsConfig onnxModelsConfig = new OnnxModelsConfig.Builder().build();
        return new RankProfilesEvaluator(config, constantsConfig, expressionsConfig, onnxModelsConfig, MockFileAcquirer.returnFile(null));
    }
}
