// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.searchlib.rankingexpression.evaluation.ArrayContext;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
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

    private ModelsEvaluator createEvaluator() {
        String configPath = "src/test/resources/config/rankexpression/rank-profiles.cfg";
        RankProfilesConfig config = new ConfigGetter<>(new FileSource(new File(configPath)), RankProfilesConfig.class).getConfig("");
        return new ModelsEvaluator(config);
    }

    @Test
    public void testScalarMapContextEvaluation() {
        ModelsEvaluator evaluator = createEvaluator();
        MapContext context = new MapContext();
        context.put("var1", 3);
        context.put("var2", 5);
        assertEquals(32.0, evaluator.evaluate("macros", "fourtimessum", context).asDouble(), delta);
    }

    @Test
    public void testTensorMapContextEvaluation() {
        ModelsEvaluator evaluator = createEvaluator();
        MapContext context = new MapContext();
        context.put("var1", Value.of(Tensor.from("{{x:0}:3,{x:1}:5}")));
        context.put("var2", Value.of(Tensor.from("{{x:0}:7,{x:1}:11}")));
        assertEquals(Tensor.from("{{x:0}:40.0,{x:1}:64.0}"), evaluator.evaluate("macros", "fourtimessum", context));
    }

    @Test
    public void testScalarArrayContextEvaluation() {
        ModelsEvaluator evaluator = createEvaluator();
        ArrayContext context = new ArrayContext(evaluator.requireModel("macros").requireFunction("fourtimessum").getBody());
        context.put("var1", Value.of(Tensor.from("{{x:0}:3,{x:1}:5}")));
        context.put("var2", Value.of(Tensor.from("{{x:0}:7,{x:1}:11}")));
        assertEquals(Tensor.from("{{x:0}:40.0,{x:1}:64.0}"), evaluator.evaluate("macros", "fourtimessum", context));
    }

    @Test
    public void testTensorArrayContextEvaluation() {
        ModelsEvaluator evaluator = createEvaluator();
        ArrayContext context = new ArrayContext(evaluator.requireModel("macros").requireFunction("fourtimessum").getBody());
        context.put("var1", Value.of(Tensor.from("{{x:0}:3,{x:1}:5}")));
        context.put("var2", Value.of(Tensor.from("{{x:0}:7,{x:1}:11}")));
        assertEquals(Tensor.from("{{x:0}:40.0,{x:1}:64.0}"), evaluator.evaluate("macros", "fourtimessum", context));
    }

    @Test
    public void testEvaluationDependingOnBoundMacro() {
        ModelsEvaluator evaluator = createEvaluator();
        Context context = evaluator.contextFor("macros", "secondphase");
        context.put("match", 3);
        context.put("rankBoost", 5);
        assertEquals(32.0, evaluator.evaluate("macros", "secondphase", context).asDouble(), delta);
    }

}
