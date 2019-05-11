// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.ImportedModel;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author lesters
 */
public class SimpleImportTestCase {

    @Test
    public void testSimpleOnnxModelImport() {
        ImportedModel model = new OnnxImporter().importModel("test", "src/test/models/onnx/simple/simple.onnx");

        MapContext context = new MapContext();
        context.put("query_tensor", new TensorValue(Tensor.Builder.of(TensorType.fromSpec("tensor(d0[1],d1[4])")).
                cell(0.1, 0, 0).
                cell(0.2, 0, 1).
                cell(0.3, 0, 2).
                cell(0.4, 0, 3).build()));
        context.put("attribute_tensor", new TensorValue(Tensor.Builder.of(TensorType.fromSpec("tensor(d0[4],d1[1])")).
                cell(0.1, 0, 0).
                cell(0.2, 1, 0).
                cell(0.3, 2, 0).
                cell(0.4, 3, 0).build()));
        context.put("bias_tensor", new TensorValue(Tensor.Builder.of(TensorType.fromSpec("tensor(d0[1],d1[1])")).
                cell(1.0, 0, 0).build()));

        Tensor result = model.expressions().get("output").evaluate(context).asTensor();
        assertEquals(result, Tensor.from("tensor(d0[1],d1[1]):{{d0:0,d1:0}:1.3}"));
    }

}
