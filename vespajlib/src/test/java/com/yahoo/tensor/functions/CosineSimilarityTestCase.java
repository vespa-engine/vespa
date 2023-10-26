// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.MapEvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.evaluation.VariableTensor;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author arnej
 */
public class CosineSimilarityTestCase {

    @Test
    public void testVectorSimilarity() {
        var a = Tensor.from("tensor(x[3]):[ 2.0,  3.0, 6.0]");
        var b = Tensor.from("tensor(x[3]):[-2.0,  0.0, 0.0]");
        var c = Tensor.from("tensor(x[3]):[ 0.0,  4.0, 3.0]");
        var op = new CosineSimilarity<>(new ConstantTensor<>(a), new ConstantTensor<>(b), "x");
        Tensor result = op.evaluate();
        assertEquals((-2.0 / 7.0), result.asDouble(), 0.000001);
        op = new CosineSimilarity<>(new ConstantTensor<>(b), new ConstantTensor<>(a), "x");
        result = op.evaluate();
        assertEquals((-2.0 / 7.0), result.asDouble(), 0.000001);
        op = new CosineSimilarity<>(new ConstantTensor<>(a), new ConstantTensor<>(c), "x");
        result = op.evaluate();
        assertEquals((30.0 / 35.0), result.asDouble(), 0.000001);
        op = new CosineSimilarity<>(new ConstantTensor<>(b), new ConstantTensor<>(c), "x");
        result = op.evaluate();
        assertEquals(0.0, result.asDouble(), 0.000001);
    }

    @Test
    public void testSimilarityInMixed() {
        var a = Tensor.from("tensor(c{},yy[3]):{foo:[3.0, 4.0,  0.0],bar:[0.0, -4.0,  3.0]}");
        var b = Tensor.from("tensor(c{},yy[3]):{foo:[0.0, 4.0, -3.0],bar:[4.0,  0.0, -3.0]}");
        var op = new CosineSimilarity<>(new ConstantTensor<>(a), new ConstantTensor<>(b), "yy");
        Tensor result = op.evaluate();
        var expect = Tensor.from("tensor(c{}):{foo:0.64,bar:-0.36}");
        assertEquals(expect, result);
    }

    static class MyContext implements TypeContext<Name> {
        Map<String, TensorType> map = new HashMap<>();
        public TensorType getType(Name name) { return getType(name.name()); }
        public TensorType getType(String name) { return map.get(name); }
    }

    @Test
    public void testExpansion() {
        var tTypeA = TensorType.fromSpec("tensor(foo{},vecdim[128])");
        var tTypeB = TensorType.fromSpec("tensor(vecdim[128],z[4])");
        var a = new VariableTensor<>("left", tTypeA);
        var b = new VariableTensor<>("right", tTypeB);
        var op = new CosineSimilarity<>(a, b, "vecdim");
        assertEquals("join(" +
                     ( "reduce(join(left, right, f(a,b)(a * b)), sum, vecdim), " +
                       "map(" +
                       ( "join(" +
                         ( "reduce(join(left, left, f(a,b)(a * b)), sum, vecdim), " +
                           "reduce(join(right, right, f(a,b)(a * b)), sum, vecdim), " +
                           "f(a,b)(a * b)), " ) +
                         "f(a)(sqrt(a))), " ) +
                       "f(a,b)(a / b)" ) +
                     ")",
                     op.toPrimitive().toString());
        var context = new MyContext();
        context.map.put("left", tTypeA);
        context.map.put("right", tTypeB);
        var resType = op.type(context);
        assertEquals("tensor(foo{},z[4])", resType.toString());
    }

}
