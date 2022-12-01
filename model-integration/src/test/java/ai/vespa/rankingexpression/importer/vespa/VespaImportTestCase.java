// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.vespa;

import ai.vespa.rankingexpression.importer.ImportedModel;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class VespaImportTestCase {

    @Test
    public void testExample() {
        ImportedModel model = importModel("example");
        assertModel(model);
    }

    @Test
    public void testLegacySyntax() {
        ImportedModel model = importModel("legacy_syntax");
        assertModel(model);
    }

    private void assertModel(ImportedModel model) {
        assertEquals(2, model.inputs().size());
        assertEquals("tensor(name{},x[3])", model.inputs().get("input1").toString());
        assertEquals("tensor(x[3])", model.inputs().get("input2").toString());

        assertEquals(2, model.smallConstantTensors().size());
        assertEquals("tensor(x[3]):[0.5, 1.5, 2.5]", model.smallConstantTensors().get("constant1").toString());
        assertEquals("tensor():{3.0}", model.smallConstantTensors().get("constant2").toString());

        assertEquals(1, model.largeConstantTensors().size());
        assertEquals("tensor(x[3]):[0.5, 1.5, 2.5]", model.largeConstantTensors().get("constant1asLarge").toString());

        assertEquals(2, model.expressions().size());
        assertEquals("reduce(reduce(input1 * input2, sum, name) * constant1, max, x) * constant2",
                     model.expressions().get("foo1").getRoot().toString());
        assertEquals("reduce(reduce(input1 * input2, sum, name) * constant(constant1asLarge), max, x) * constant2",
                     model.expressions().get("foo2").getRoot().toString());

        List<ImportedMlFunction> functions = model.outputExpressions();
        assertEquals(2, functions.size());
        ImportedMlFunction foo1Function = functions.get(0);
        assertEquals("foo1", foo1Function.name());
        assertEquals("reduce(reduce(input1 * input2, sum, name) * constant1, max, x) * constant2", foo1Function.expression());
        assertEquals("tensor():{202.5}", evaluate(foo1Function, "{{name:a, x:0}: 1, {name:a, x:1}: 2, {name:a, x:2}: 3}").toString());
        assertEquals(2, foo1Function.arguments().size());
        assertTrue(foo1Function.arguments().contains("input1"));
        assertTrue(foo1Function.arguments().contains("input2"));
        assertEquals(2, foo1Function.argumentTypes().size());
        assertEquals("tensor(name{},x[3])", foo1Function.argumentTypes().get("input1"));
        assertEquals("tensor(x[3])", foo1Function.argumentTypes().get("input2"));
    }

    @Test
    public void testEmpty() {
        ImportedModel model = importModel("empty");
        assertTrue(model.expressions().isEmpty());
        assertTrue(model.functions().isEmpty());
        assertTrue(model.inputs().isEmpty());
        assertTrue(model.largeConstantTensors().isEmpty());
        assertTrue(model.smallConstantTensors().isEmpty());
    }

    @Test
    public void testWrongName() {
        try {
            importModel("misnamed");
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Unexpected model name 'misnamed': " +
                         "Model 'expectedname' must be saved in a file named 'expectedname.model'", e.getMessage());
        }
    }

    private ImportedModel importModel(String name) {
        String modelPath = "src/test/models/vespa/" + name + ".model";

        VespaImporter importer = new VespaImporter();
        assertTrue(importer.canImport(modelPath));
        ImportedModel model = new VespaImporter().importModel(name, modelPath);
        assertEquals(name, model.name());
        assertEquals(modelPath, model.source());
        return model;
    }

    private Tensor evaluate(ImportedMlFunction function, String input1Argument) {
        try {
            MapContext context = new MapContext();
            context.put("input1", new TensorValue(Tensor.from(function.argumentTypes().get("input1"), input1Argument)));
            context.put("input2", new TensorValue(Tensor.from(function.argumentTypes().get("input2"), "{{x:0}:3, {x:1}:6, {x:2}:9}")));
            context.put("constant1", new TensorValue(Tensor.from("tensor(x[3]):{{x:0}:0.5, {x:1}:1.5, {x:2}:2.5}")));
            context.put("constant2", new TensorValue(Tensor.from("tensor():{{}:3}")));
            return new RankingExpression(function.expression()).evaluate(context).asTensor();
        }
        catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
