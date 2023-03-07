// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import com.yahoo.path.Path;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class ModelsEvaluatorTest {

    private static final double delta = 0.00000000001;
    private static final String CONFIG_DIR = "src/test/resources/config/rankexpression/";

    @Test
    public void testEvaluationDependingFunctionTakingArguments() {
        ModelsEvaluator models = createModels();
        FunctionEvaluator function = models.evaluatorOf("macros", "secondphase");
        function.bind("match", 3);
        function.bind("rankBoost", 5);
        assertEquals(32.0, function.evaluate().asDouble(), delta);
    }

    /** Tests a function defined as 4 * (var1 + var2) */
    @Test
    public void testSettingMissingValue() {
        ModelsEvaluator models = createModels();

        {
            FunctionEvaluator function = models.evaluatorOf("macros", "secondphase");
            assertTrue(Double.isNaN(function.evaluate().asDouble()));
        }

        {
            FunctionEvaluator function = models.evaluatorOf("macros", "secondphase");
            function.setMissingValue(5);
            assertEquals(40.0, function.evaluate().asDouble(), delta);
        }

        {
            FunctionEvaluator function = models.evaluatorOf("macros", "secondphase");
            function.setMissingValue(5);
            function.bind("match", 3);
            assertEquals(32.0, function.evaluate().asDouble(), delta);
        }
    }

    @Test
    public void testBindingValidation() {
        List<ExpressionFunction> functions = new ArrayList<>();
        ExpressionFunction function = new ExpressionFunction("test", RankingExpression.from("sum(arg1 * arg2)"));
        function = function.withArgument("arg1", TensorType.fromSpec("tensor(d0[1])"));
        function = function.withArgument("arg2", TensorType.fromSpec("tensor(d1{})"));
        functions.add(function);
        Model model = new Model("test-model", functions);

        try { // No bindings
            FunctionEvaluator evaluator = model.evaluatorOf("test");
            evaluator.evaluate();
        }
        catch (IllegalStateException e) {
            assertEquals("Argument 'arg1' must be bound to a value of type tensor(d0[1])",
                         Exceptions.toMessageString(e));
        }

        try { // Just one binding
            FunctionEvaluator evaluator = model.evaluatorOf("test");
            evaluator.bind("arg2", Tensor.from(TensorType.fromSpec("tensor(d1{})"), "{{d1:foo}:0.1}"));
            evaluator.evaluate();
        }
        catch (IllegalStateException e) {
            assertEquals("Argument 'arg1' must be bound to a value of type tensor(d0[1])",
                         Exceptions.toMessageString(e));
        }
        try { // Just the other binding
            FunctionEvaluator evaluator = model.evaluatorOf("test");
            evaluator.bind("arg1", Tensor.from(TensorType.fromSpec("tensor(d0[1])"), "{{d0:0}:0.1}"));
            evaluator.evaluate();
        }
        catch (IllegalStateException e) {
            assertEquals("Argument 'arg2' must be bound to a value of type tensor(d1{})",
                    Exceptions.toMessageString(e));
        }

        try { // Wrong binding argument
            FunctionEvaluator evaluator = model.evaluatorOf("test");
            evaluator.bind("argNone", Tensor.from(TensorType.fromSpec("tensor(d1{})"), "{{d1:foo}:0.1}"));
            evaluator.evaluate();
        }
        catch (IllegalArgumentException e) {
            assertEquals("'argNone' is not a valid argument in function 'test'. Expected arguments: arg1: tensor(d0[1]), arg2: tensor(d1{})",
                         Exceptions.toMessageString(e));
        }

        try { // Wrong binding type
            FunctionEvaluator evaluator = model.evaluatorOf("test");
            evaluator.bind("arg1", Tensor.from(TensorType.fromSpec("tensor(d3{})"), "{{d3:foo}:0.1}"));
            evaluator.evaluate();
        }
        catch (IllegalArgumentException e) {
            assertEquals("'arg1' must be of type tensor(d0[1]), not tensor(d3{})",
                         Exceptions.toMessageString(e));
        }

        try { // Attempt to reuse evaluator
            FunctionEvaluator evaluator = model.evaluatorOf("test");
            evaluator.bind("arg1", Tensor.from(TensorType.fromSpec("tensor(d0[1])"), "{{d0:0}:0.1}"));
            evaluator.bind("arg2", Tensor.from(TensorType.fromSpec("tensor(d1{})"), "{{d1:foo}:0.1}"));
            evaluator.evaluate();
            evaluator.bind("arg1", Tensor.from(TensorType.fromSpec("tensor(d0[1])"), "{{d0:0}:0.1}"));
        }
        catch (IllegalStateException e) {
            assertEquals("Cannot bind a new value in a used evaluator",
                         Exceptions.toMessageString(e));
        }

    }

    // TODO: Test argument-less function
    // TODO: Test with nested functions

    @SuppressWarnings("deprecation")
    private ModelsEvaluator createModels() {
        RankProfilesConfig config = ConfigGetter.getConfig(RankProfilesConfig.class, fileConfigId("rank-profiles.cfg"));
        RankingConstantsConfig constantsConfig = ConfigGetter.getConfig(RankingConstantsConfig.class, fileConfigId("ranking-constants.cfg"));
        RankingExpressionsConfig expressionsConfig = ConfigGetter.getConfig(RankingExpressionsConfig.class, fileConfigId("ranking-expressions.cfg"));
        OnnxModelsConfig onnxModelsConfig = ConfigGetter.getConfig(OnnxModelsConfig.class, fileConfigId("onnx-models.cfg"));

        return new ModelsEvaluator(new RankProfilesConfigImporterWithMockedConstants(Path.fromString(CONFIG_DIR), MockFileAcquirer.returnFile(null)),
                config, constantsConfig, expressionsConfig, onnxModelsConfig);
    }

    private static String fileConfigId(String filename) {
        return "file:" + CONFIG_DIR + filename;
    }

}
