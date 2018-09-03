// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.tensor.Tensor;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ModelsEvaluatorTest {

    private static final double delta = 0.00000000001;

    private ModelsEvaluator createModels() {
        String configPath = "src/test/resources/config/rankexpression/rank-profiles.cfg";
        RankProfilesConfig config = new ConfigGetter<>(new FileSource(new File(configPath)), RankProfilesConfig.class).getConfig("");
        return new ModelsEvaluator(config);
    }

    @Test
    public void testTensorEvaluation() {
        ModelsEvaluator models = createModels();
        FunctionEvaluator function = models.evaluatorOf("macros", "fourtimessum");
        function.bind("var1", Tensor.from("{{x:0}:3,{x:1}:5}"));
        function.bind("var2", Tensor.from("{{x:0}:7,{x:1}:11}"));
        assertEquals(Tensor.from("{{x:0}:40.0,{x:1}:64.0}"), function.evaluate());
    }

    @Test
    public void testEvaluationDependingOnMacroTakingArguments() {
        ModelsEvaluator models = createModels();
        FunctionEvaluator function = models.evaluatorOf("macros", "secondphase");
        function.bind("match", 3);
        function.bind("rankBoost", 5);
        assertEquals(32.0, function.evaluate().asDouble(), delta);
    }

    // TODO: Test argument-less function
    // TODO: Test that binding nonexisting variable doesn't work
    // TODO: Test that rebinding doesn't work
    // TODO: Test with nested macros

}
