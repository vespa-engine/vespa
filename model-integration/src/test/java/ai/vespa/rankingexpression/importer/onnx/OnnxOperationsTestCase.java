// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.IntermediateGraph;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import ai.vespa.rankingexpression.importer.operations.Constant;
import ai.vespa.rankingexpression.importer.operations.IntermediateOperation;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.ConstantTensor;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.TensorFunction;
import onnx.Onnx;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static ai.vespa.rankingexpression.importer.onnx.GraphImporter.*;
import static onnx.Onnx.AttributeProto.AttributeType.FLOAT;
import static onnx.Onnx.AttributeProto.AttributeType.INT;
import static onnx.Onnx.AttributeProto.AttributeType.INTS;
import static onnx.Onnx.AttributeProto.AttributeType.TENSOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Unit tests for ONNX operators. The number on the test reflects the minimum
 * opset number for the operations tested.
 *
 * @author lesters
 */
public class OnnxOperationsTestCase {

    private static final String modelName = "test_model";

    @Test
    public void testElementwiseOperators7() throws ParseException {
        Tensor x = evaluate("tensor(d0[7]):[-1.0, -0.5, -0.1, 0.0, 0.1, 0.5, 1.0]");
        assertEval("acos", x, evaluate("acos(x)", x));
        assertEval("asin", x, evaluate("asin(x)", x));
        assertEval("atan", x, evaluate("atan(x)", x));
        assertEval("cos", x, evaluate("cos(x)", x));
        assertEval("sin", x, evaluate("sin(x)", x));
        assertEval("tan", x, evaluate("tan(x)", x));
        assertEval("tanh", x, evaluate("tanh(x)", x));
        assertEval("neg", x, evaluate("-x", x));
        assertEval("sigmoid", x, evaluate("sigmoid(x)", x));
        assertEval("exp", x, evaluate("exp(x)", x));
        assertEval("floor", x, evaluate("floor(x)", x));
        assertEval("ceil", x, evaluate("ceil(x)", x));
        assertEval("abs", x, evaluate("abs(x)", x));

        assertEval("relu", x, evaluate("max(0, x)", x));
        assertEval("elu", x, evaluate("map(x, f(a)(if(a < 0, 1.0 * (exp(a)-1), a)))", x));
        assertEval("elu", x, evaluate("map(x, f(a)(if(a < 0, 0.5 * (exp(a)-1), a)))", x), createAttribute("alpha", 0.5f));
        assertEval("selu", x, evaluate("map(x, f(a)(1.050700987 * if(a >= 0, a, 1.673263242 * (exp(a) - 1))))", x));
        assertEval("selu", x, evaluate("map(x, f(a)(1.0 * if(a >= 0, a, 1.5 * (exp(a) - 1))))", x), createAttributes().attr("gamma", 1.0f).attr("alpha", 1.5f).build());
        assertEval("leakyrelu", x, evaluate("max(0.01 * x, x)", x));
        assertEval("leakyrelu", x, evaluate("max(0.001 * x, x)", x), createAttribute("alpha", 0.001f));

        x = evaluate("tensor(d0[7]):[-40.0, -0.5, -0.1, 0.0, 0.1, 0.5, 40.0]");
        assertEval("erf", x, evaluate("erf(x)", x));

        x = evaluate("tensor(d0[3]):[0.01, 1.0, 10.0]");
        assertEval("log", x, evaluate("log(x)", x));
        assertEval("sqrt", x, evaluate("sqrt(x)", x));
        assertEval("reciprocal", x, evaluate("map(x, f(a)(1.0 / a))", x));
    }

    @Test
    public void testJoinOperators7() throws ParseException {
        Tensor x = evaluate("tensor(d0[2]):[3, 4]");
        Tensor y = evaluate("tensor(d0[2]):[1, 2]");
        assertEval("add", x, y, evaluate("tensor(d0[2]):[4, 6]"));
        assertEval("sub", x, y, evaluate("tensor(d0[2]):[2, 2]"));
        assertEval("mul", x, y, evaluate("tensor(d0[2]):[3, 8]"));
        assertEval("div", x, y, evaluate("tensor(d0[2]):[3, 2]"));
        assertEval("greater", x, y, evaluate("tensor(d0[2]):[1, 1]"));
        assertEval("less", x, y, evaluate("tensor(d0[2]):[0, 0]"));
        assertEval("equal", x, y, evaluate("tensor(d0[2]):[0, 0]"));
        assertEval("pow", x, y, evaluate("tensor(d0[2]):[3, 16]"));

        x = evaluate("random(d0[2],d1[3],d2[4]) + 1");
        y = evaluate("random(d0[2],d1[3],d2[4]) + 1");
        assertEval("add", x, y, evaluate("x + y", x, y));
        assertEval("sub", x, y, evaluate("x - y", x, y));
        assertEval("mul", x, y, evaluate("x * y", x, y));
        assertEval("div", x, y, evaluate("x / y", x, y));
        assertEval("greater", x, y, evaluate("join(x, y, f(a,b)(a > b))", x, y));
        assertEval("less", x, y, evaluate("join(x, y, f(a,b)(a < b))", x, y));
        assertEval("equal", x, y, evaluate("join(x, y, f(a,b)(a == b))", x, y));
        assertEval("pow", x, y, evaluate("join(x, y, f(a,b)(pow(a,b)))", x, y));

        // broadcasting
        x = evaluate("random(d0[2],d1[3],d2[4]) + 1");
        y = evaluate("random(d0[4]) + 1");
        assertEval("add", x, y, evaluate("x + rename(y, d0, d2)", x, y));
        assertEval("sub", x, y, evaluate("x - rename(y, d0, d2)", x, y));
        assertEval("mul", x, y, evaluate("x * rename(y, d0, d2)", x, y));
        assertEval("div", x, y, evaluate("x / rename(y, d0, d2)", x, y));
        assertEval("greater", x, y, evaluate("join(x, rename(y, d0, d2), f(a,b)(a > b))", x, y));
        assertEval("less", x, y, evaluate("join(x, rename(y, d0, d2), f(a,b)(a < b))", x, y));
        assertEval("equal", x, y, evaluate("join(x, rename(y, d0, d2), f(a,b)(a == b))", x, y));
        assertEval("pow", x, y, evaluate("join(x, rename(y, d0, d2), f(a,b)(pow(a,b)))", x, y));

        // broadcasting - opposite order
        x = evaluate("random(d0[4]) + 1");
        y = evaluate("random(d0[2],d1[3],d2[4]) + 1");
        assertEval("add", x, y, evaluate("rename(x, d0, d2) + y", x, y));
        assertEval("sub", x, y, evaluate("rename(x, d0, d2) - y", x, y));
        assertEval("mul", x, y, evaluate("rename(x, d0, d2) * y", x, y));
        assertEval("div", x, y, evaluate("rename(x, d0, d2) / y", x, y));
        assertEval("greater", x, y, evaluate("join(rename(x, d0, d2), y, f(a,b)(a > b))", x, y));
        assertEval("less", x, y, evaluate("join(rename(x, d0, d2), y, f(a,b)(a < b))", x, y));
        assertEval("equal", x, y, evaluate("join(rename(x, d0, d2), y, f(a,b)(a == b))", x, y));
        assertEval("pow", x, y, evaluate("join(rename(x, d0, d2), y, f(a,b)(pow(a,b)))", x, y));
    }

    @Test
    public void testConcatReduce8() throws ParseException {
        Tensor x = evaluate("tensor(d0[2]):[3, 4]");
        Tensor y = evaluate("tensor(d0[2]):[1, 2]");
        Tensor z = evaluate("tensor(d0[2]):[5, 6]");
        assertEval("max", x, y, z, evaluate("tensor(d0[2]):[5, 6]"));
        assertEval("min", x, y, z, evaluate("tensor(d0[2]):[1, 2]"));
        assertEval("mean", x, y, z, evaluate("tensor(d0[2]):[3, 4]"));

        x = evaluate("random(d0[2],d1[3],d2[4])");
        y = evaluate("random(d0[2],d1[3],d2[4])");
        z = evaluate("random(d0[2],d1[3],d2[4])");
        assertEval("max", x, y, z, evaluate("reduce(concat(concat(x, y, tmp), z, tmp), max, tmp)", x, y, z));
        assertEval("min", x, y, z, evaluate("reduce(concat(concat(x, y, tmp), z, tmp), min, tmp)", x, y, z));
        assertEval("mean", x, y, z, evaluate("reduce(concat(concat(x, y, tmp), z, tmp), avg, tmp)", x, y, z));

        // broadcasting
        x = evaluate("random(d0[2],d1[3],d2[4])");
        y = evaluate("random(d0[3],d1[4])");
        z = evaluate("random(d0[4])");
        assertEval("max", x, y, z, evaluate("reduce(concat(concat(x, rename(y, (d0,d1), (d1,d2)), tmp), rename(z, d0, d2), tmp), max, tmp)", x, y, z));
        assertEval("min", x, y, z, evaluate("reduce(concat(concat(x, rename(y, (d0,d1), (d1,d2)), tmp), rename(z, d0, d2), tmp), min, tmp)", x, y, z));
        assertEval("mean", x, y, z, evaluate("reduce(concat(concat(x, rename(y, (d0,d1), (d1,d2)), tmp), rename(z, d0, d2), tmp), avg, tmp)", x, y, z));
    }

    @Test
    public void testConcat4() throws ParseException {
        Tensor x = evaluate("tensor(d0[2]):[1, 2]");
        Tensor y = evaluate("tensor(d0[2]):[3, 4]");
        Tensor expected = evaluate("tensor(d0[4]):[1,2,3,4]");
        assertEval("concat", x, y, expected, createAttribute("axis", 0));
        assertEval("concat", x, y, expected, createAttribute("axis", -1));

        x = evaluate("tensor(d0[2],d1[2]):[1, 2, 3, 4]");
        y = evaluate("tensor(d0[2],d1[2]):[5, 6, 7, 8]");
        assertEval("concat", x, y, evaluate("tensor(d0[4],d1[2]):[1,2,3,4,5,6,7,8]"), createAttribute("axis", 0));
        assertEval("concat", x, y, evaluate("tensor(d0[2],d1[4]):[1,2,5,6,3,4,7,8]"), createAttribute("axis", 1));
        assertEval("concat", x, y, evaluate("tensor(d0[2],d1[4]):[1,2,5,6,3,4,7,8]"), createAttribute("axis", -1));
        assertEval("concat", x, y, evaluate("tensor(d0[4],d1[2]):[1,2,3,4,5,6,7,8]"), createAttribute("axis", -2));

        x = evaluate("tensor(d0[2],d1[2],d2[2]):[1, 2, 3, 4, 5, 6, 7, 8]");
        y = evaluate("tensor(d0[2],d1[2],d2[2]):[9,10,11,12,13,14,15,16]");
        assertEval("concat", x, y, evaluate("concat(x, y, d0)", x, y), createAttribute("axis", 0));
        assertEval("concat", x, y, evaluate("concat(x, y, d1)", x, y), createAttribute("axis", 1));
        assertEval("concat", x, y, evaluate("concat(x, y, d2)", x, y), createAttribute("axis", 2));
        assertEval("concat", x, y, evaluate("concat(x, y, d2)", x, y), createAttribute("axis", -1));
        assertEval("concat", x, y, evaluate("concat(x, y, d1)", x, y), createAttribute("axis", -2));
        assertEval("concat", x, y, evaluate("concat(x, y, d0)", x, y), createAttribute("axis", -3));
    }

    @Test
    public void testGemm7() throws ParseException {
        Tensor a = evaluate("tensor(d0[2],d1[2]):[1, 2, 3, 4]");
        Tensor b = evaluate("tensor(d0[2],d1[2]):[5, 6, 7, 8]");
        Tensor c = evaluate("tensor(d0[2],d1[2]):[0.1, 0.2, 0.3, 0.4]");

        assertEval("gemm", a, b, evaluate("tensor(d0[2],d1[2]):[19, 22, 43, 50]"));
        assertEval("gemm", a, b, c, evaluate("tensor(d0[2],d1[2]):[19.1, 22.2, 43.3, 50.4]"));
        assertEval("gemm", a, b, c, evaluate("tensor(d0[2],d1[2]):[38.1, 44.2, 86.3, 100.4]"), createAttribute("alpha", 2.0f));
        assertEval("gemm", a, b, c, evaluate("tensor(d0[2],d1[2]):[19.2, 22.4, 43.6, 50.8]"), createAttribute("beta", 2.0f));
        assertEval("gemm", a, b, c, evaluate("tensor(d0[2],d1[2]):[26.1, 30.2, 38.3, 44.4]"), createAttribute("transA", 1));
        assertEval("gemm", a, b, c, evaluate("tensor(d0[2],d1[2]):[17.1, 23.2, 39.3, 53.4]"), createAttribute("transB", 1));

        // unidictional broadcasting for c
        c = evaluate("tensor(d0[2]):[0.1, 0.2]");
        assertEval("gemm", a, b, c, evaluate("tensor(d0[2],d1[2]):[19.1, 22.2, 43.1, 50.2]"));
    }

    @Test
    public void testIdentity1() throws ParseException {
        Tensor x = evaluate("random(d0[2],d1[3],d2[4])");
        assertEval("identity", x, x);
    }

    @Test
    public void testMatMul1() throws ParseException {
        Tensor a = evaluate("tensor(d0[6]):[1,2,3,4,5,6]");
        Tensor b = evaluate("tensor(d0[6]):[1,2,3,4,5,6]");
        assertEval("matmul", a, b, evaluate("91"));

        a = evaluate("tensor(d0[3]):[1,2,3]");
        b = evaluate("tensor(d0[3],d1[2]):[1,2,3,4,5,6]");
        assertEval("matmul", a, b, evaluate("tensor(d0[2]):[22, 28]"));

        a = evaluate("tensor(d0[2],d1[3]):[1,2,3,4,5,6]");
        b = evaluate("tensor(d0[3]):[1,2,3]");
        assertEval("matmul", a, b, evaluate("tensor(d0[2]):[14, 32]"));

        a = evaluate("tensor(d0[2],d1[3]):[1,2,3,4,5,6]");
        b = evaluate("tensor(d0[3],d1[2]):[1,2,3,4,5,6]");
        assertEval("matmul", a, b, evaluate("tensor(d0[2],d1[2]):[22,28,49,64]"));

        a = evaluate("tensor(d0[1],d1[2],d2[3]):[1,2,3,4,5,6]");
        b = evaluate("tensor(d0[3],d1[2]):[1,2,3,4,5,6]");
        assertEval("matmul", a, b, evaluate("tensor(d0[1],d1[2],d2[2]):[22,28,49,64]"));

        a = evaluate("tensor(d0[2],d1[3]):[1,2,3,4,5,6]");
        b = evaluate("tensor(d0[1],d1[3],d2[2]):[1,2,3,4,5,6]");
        assertEval("matmul", a, b, evaluate("tensor(d0[1],d1[2],d2[2]):[22,28,49,64]"));

        a = evaluate("tensor(d0[1],d1[2],d2[3]):[1,2,3,4,5,6]");
        b = evaluate("tensor(d0[1],d1[3],d2[2]):[1,2,3,4,5,6]");
        assertEval("matmul", a, b, evaluate("tensor(d0[1],d1[2],d2[2]):[22,28,49,64]"));

        a = evaluate("tensor(d0[1],d1[2],d2[3]):[1,2,3,4,5,6]");
        b = evaluate("tensor(d0[2],d1[3],d2[2]):[1,2,3,4,5,6,7,8,9,10,11,12]");
        assertEval("matmul", a, b, evaluate("tensor(d0[2],d1[2],d2[2]):[22,28,49,64,58,64,139,154]"));

        a = evaluate("tensor(d0[2],d1[2],d2[3]):[1,2,3,4,5,6,7,8,9,10,11,12]");
        b = evaluate("tensor(d0[1],d1[3],d2[2]):[1,2,3,4,5,6]");
        assertEval("matmul", a, b, evaluate("tensor(d0[2],d1[2],d2[2]):[22,28,49,64,76,100,103,136]"));

        a = evaluate("tensor(d0[1],d1[4],d2[2],d3[3]):[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24]");
        b = evaluate("tensor(d0[1],d1[4],d2[3],d3[2]):[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24]");
        assertEval("matmul", a, b, evaluate("tensor(d0[1],d1[4],d2[2],d3[2]):[22,28,49,64,220,244,301,334,634,676,769,820,1264,1324,1453,1522]"));

        a = evaluate("tensor(d0[1],d1[1],d2[2],d3[3]):[1,2,3,4,5,6]");
        b = evaluate("tensor(d0[2],d1[2],d2[3],d3[2]):[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24]");
        assertEval("matmul", a, b, evaluate("tensor(d0[2],d1[2],d2[2],d3[2]):[22,28,49,64,58,64,139,154,94,100,229,244,130,136,319,334]"));
    }

    @Test
    public void testReshape5() throws ParseException {
        Tensor x = evaluate("tensor(d0[2],d1[2]):[1,2,3,4]");
        Tensor y = evaluate("tensor(d0[1]):[4]");
        assertEval("reshape", x, y, evaluate("tensor(d0[4]):[1,2,3,4]"));

        y = evaluate("tensor(d0[2]):[2,2]");
        assertEval("reshape", x, y, evaluate("tensor(d0[2],d1[2]):[1,2,3,4]"));

        y = evaluate("tensor(d0[3]):[2,1,2]");
        assertEval("reshape", x, y, evaluate("tensor(d0[2],d1[1],d2[2]):[1,2,3,4]"));

        y = evaluate("tensor(d0[2]):[2,-1]");
        assertEval("reshape", x, y, evaluate("tensor(d0[2],d1[2]):[1,2,3,4]"));

        y = evaluate("tensor(d0[2]):[2,0]");
        assertEval("reshape", x, y, evaluate("tensor(d0[2],d1[2]):[1,2,3,4]"));

        y = evaluate("tensor(d0[2]):[0,-1]");
        assertEval("reshape", x, y, evaluate("tensor(d0[2],d1[2]):[1,2,3,4]"));

        x = evaluate("tensor(d0[1],d1[2],d2[3]):[1,2,3,4,5,6]");
        y = evaluate("tensor(d0[2]):[3,2]");
        assertEval("reshape", x, y, evaluate("tensor(d0[3],d1[2]):[1,2,3,4,5,6]"));

        y = evaluate("tensor(d0[4]):[3,2,-1,1]");
        assertEval("reshape", x, y, evaluate("tensor(d0[3],d1[2],d2[1],d3[1]):[1,2,3,4,5,6]"));

        x = evaluate("tensor(d0[1],d1[2],d2[2],d3[3]):[1,2,3,4,5,6,7,8,9,10,11,12]");
        y = evaluate("tensor(d0[2]):[2,6]");
        assertEval("reshape", x, y, evaluate("tensor(d0[2],d1[6]):[1,2,3,4,5,6,7,8,9,10,11,12]"));
    }

    @Test
    public void testReduceOperators1() throws ParseException {
        Tensor x = evaluate("tensor(d0[2],d1[2]):[1, 2, 3, 4]");

        assertEval("reducesum", x, evaluate("tensor(d0[1],d1[1]):[10]"));
        assertEval("reducesum", x, evaluate("tensor(d0[1],d1[1]):[10]"), createAttribute("axes", new int[] {0,1}));
        assertEval("reducesum", x, evaluate("tensor():[10]"), createAttribute("keepdims", 0));
        assertEval("reducesum", x, evaluate("tensor(d0[1],d1[1]):[10]"), createAttribute("keepdims", 1));
        assertEval("reducesum", x, evaluate("tensor(d0[1],d1[2]):[4, 6]"), createAttribute("axes", new int[]{0}));
        assertEval("reducesum", x, evaluate("tensor(d0[2]):[4, 6]"), createAttributes().attr("axes", new int[]{0}).attr("keepdims", 0).build());
        assertEval("reducesum", x, evaluate("tensor(d0[2],d1[1]):[3, 7]"), createAttribute("axes", new int[] {1}));
        assertEval("reducesum", x, evaluate("tensor(d0[2]):[3, 7]"), createAttributes().attr("axes", new int[]{1}).attr("keepdims", 0).build());
        assertEval("reducesum", x, evaluate("tensor(d0[1],d1[2]):[4, 6]"), createAttribute("axes", new int[] {-2}));
        assertEval("reducesum", x, evaluate("tensor(d0[2],d1[1]):[3, 7]"), createAttribute("axes", new int[] {-1}));
        assertEval("reducesum", x, evaluate("tensor(d0[2]):[3, 7]"), createAttributes().attr("axes", new int[] {-1}).attr("keepdims", 0).build());

        assertEval("reduceprod", x, evaluate("tensor(d0[1],d1[1]):[24]"));
        assertEval("reduceprod", x, evaluate("tensor(d0[1],d1[2]):[3, 8]"), createAttribute("axes", new int[] {0}));

        assertEval("reducemin", x, evaluate("tensor(d0[1],d1[1]):[1]"));
        assertEval("reducemin", x, evaluate("tensor(d0[1],d1[2]):[1, 2]"), createAttribute("axes", new int[] {0}));

        assertEval("reducemax", x, evaluate("tensor(d0[1],d1[1]):[4]"));
        assertEval("reducemax", x, evaluate("tensor(d0[1],d1[2]):[3, 4]"), createAttribute("axes", new int[] {0}));

        assertEval("reducemean", x, evaluate("tensor():[2.5]"), createAttribute("keepdims", 0));
        assertEval("reducemean", x, evaluate("tensor(d0[2]):[2, 3]"), createAttributes().attr("axes", new int[] {0}).attr("keepdims", 0).build());

        assertEval("reducelogsum", x, evaluate("tensor():[log(10)]"), createAttribute("keepdims", 0));
        assertEval("reducelogsumexp", x, evaluate("tensor():[log(exp(1)+exp(2)+exp(3)+exp(4))]"), createAttribute("keepdims", 0));
        assertEval("reducesumsquare", x, evaluate("tensor():[1*1+2*2+3*3+4*4]"), createAttribute("keepdims", 0));

        x = evaluate("tensor(d0[1],d1[5]):[-10, -5, 0, 5, 10]");
        assertEval("reducel1", x, evaluate("tensor():[30]"), createAttribute("keepdims", 0));
        assertEval("reducel2", x, evaluate("tensor():[sqrt(10*10 + 5*5 + 5*5 + 10*10)]"), createAttribute("keepdims", 0));
    }

    @Test
    public void testShape1() throws ParseException {
        Tensor x = evaluate("random(d0[2],d1[3],d2[4])");
        assertEval("shape", x, evaluate("tensor(d0[3]):[2,3,4]"));
    }

    @Test
    public void testSoftmax1() throws ParseException {
        Tensor x = evaluate("tensor(d0[1],d1[3]):[-1, 0, 1]");
        assertEval("softmax", x, evaluate("tensor(d0[1],d1[3]):[0.09003058, 0.24472848, 0.66524094]"));

        x = evaluate("tensor(d0[2],d1[3]):[1, 2, 3, 4, 5, 7]");
        assertEval("softmax", x, evaluate("exp(x) / sum(exp(x), d1)", x));
        assertEval("softmax", x, evaluate("exp(x) / sum(exp(x), d0, d1)", x), createAttribute("axis", 0));
        assertEval("softmax", x, evaluate("exp(x) / sum(exp(x), d1)", x), createAttribute("axis", 1)); // 1 is default
        assertEval("softmax", x, evaluate("exp(x) / sum(exp(x), d1)", x), createAttribute("axis", -1));
        assertEval("softmax", x, evaluate("exp(x) / sum(exp(x), d0, d1)", x), createAttribute("axis", -2));

        x = evaluate("random(d0[2],d1[3],d2[4])");
        assertEval("softmax", x, evaluate("exp(x) / sum(exp(x), d1, d2)", x));
        assertEval("softmax", x, evaluate("exp(x) / sum(exp(x), d0, d1, d2)", x), createAttribute("axis", 0));
        assertEval("softmax", x, evaluate("exp(x) / sum(exp(x), d1, d2)", x), createAttribute("axis", 1));
        assertEval("softmax", x, evaluate("exp(x) / sum(exp(x), d2)", x), createAttribute("axis", 2));
        assertEval("softmax", x, evaluate("exp(x) / sum(exp(x), d2)", x), createAttribute("axis", -1));
        assertEval("softmax", x, evaluate("exp(x) / sum(exp(x), d1, d2)", x), createAttribute("axis", -2));
        assertEval("softmax", x, evaluate("exp(x) / sum(exp(x), d0, d1, d2)", x), createAttribute("axis", -3));
    }

    @Test
    public void testSqueeze1() throws ParseException {
        Tensor x = evaluate("tensor(d0[1],d1[2]):[1, 2]");
        assertEval("squeeze", x, evaluate("tensor(d0[2]):[1, 2]"));

        x = evaluate("tensor(d0[1],d1[2],d2[1],d3[3]):[1,2,3,4,5,6]");
        assertEval("squeeze", x, evaluate("tensor(d0[2],d1[3]):[1,2,3,4,5,6]"));
        assertEval("squeeze", x, evaluate("tensor(d0[2],d1[1],d2[3]):[1,2,3,4,5,6]"), createAttribute("axes", new int[] {0}));
        assertEval("squeeze", x, evaluate("tensor(d0[1],d1[2],d2[3]):[1,2,3,4,5,6]"), createAttribute("axes", new int[] {2}));
        assertEval("squeeze", x, evaluate("tensor(d0[2],d1[3]):[1,2,3,4,5,6]"), createAttribute("axes", new int[] {0, 2}));
    }

    @Test
    public void testUnsqueeze1() throws ParseException {
        Tensor x = evaluate("tensor(d0[2]):[1, 2]");
        assertEval("unsqueeze", x, evaluate("tensor(d0[1],d1[2]):[1, 2]"), createAttribute("axes", new int[] {0}));
        assertEval("unsqueeze", x, evaluate("tensor(d0[2],d1[1]):[1, 2]"), createAttribute("axes", new int[] {1}));
        assertEval("unsqueeze", x, evaluate("tensor(d0[2],d1[1]):[1, 2]"), createAttribute("axes", new int[] {-1}));
        assertEval("unsqueeze", x, evaluate("tensor(d0[1],d1[2]):[1, 2]"), createAttribute("axes", new int[] {-2}));
        assertEval("unsqueeze", x, evaluate("tensor(d0[1],d1[2]):[1, 2]"), createAttribute("axes", new int[] {0,0}));
        assertEval("unsqueeze", x, evaluate("tensor(d0[1],d1[2],d2[1]):[1, 2]"), createAttribute("axes", new int[] {0,2}));
        assertEval("unsqueeze", x, evaluate("tensor(d0[1],d1[2],d2[1]):[1, 2]"), createAttribute("axes", new int[] {2,0}));

        x = evaluate("tensor(d0[2],d1[3]):[1,2,3,4,5,6]");
        assertEval("unsqueeze", x, evaluate("tensor(d0[1],d1[1],d2[2],d3[3]):[1,2,3,4,5,6]"), createAttribute("axes", new int[] {0,1}));
        assertEval("unsqueeze", x, evaluate("tensor(d0[1],d1[2],d2[1],d3[3]):[1,2,3,4,5,6]"), createAttribute("axes", new int[] {0,2}));
        assertEval("unsqueeze", x, evaluate("tensor(d0[1],d1[2],d2[3],d3[1]):[1,2,3,4,5,6]"), createAttribute("axes", new int[] {0,3}));
        assertEval("unsqueeze", x, evaluate("tensor(d0[2],d1[1],d2[1],d3[3]):[1,2,3,4,5,6]"), createAttribute("axes", new int[] {1,2}));
        assertEval("unsqueeze", x, evaluate("tensor(d0[2],d1[3],d2[1],d3[1]):[1,2,3,4,5,6]"), createAttribute("axes", new int[] {2,3}));
        assertEval("unsqueeze", x, evaluate("tensor(d0[1],d1[2],d2[1],d3[3],d4[1]):[1,2,3,4,5,6]"), createAttribute("axes", new int[] {0,2,4}));
        assertEval("unsqueeze", x, evaluate("tensor(d0[1],d1[2],d2[1],d3[3],d4[1]):[1,2,3,4,5,6]"), createAttribute("axes", new int[] {4,2,0}));
    }

    @Test
    public void testWhere9() throws ParseException {
        Tensor x = evaluate("tensor(d0[2],d1[2]):[1, 2, 3, 4]");
        Tensor y = evaluate("tensor(d0[2],d1[2]):[5, 6, 7, 8]");
        Tensor condition = evaluate("tensor(d0[2],d1[2]):[0, 1, 0, 1]");
        assertEval("where", condition, x, y, evaluate("tensor(d0[2],d1[2]):[5, 2, 7, 4]"));

        assertEval("where", evaluate("tensor():[0]"), x, y, y);
        assertEval("where", evaluate("tensor():[1]"), x, y, x);
        assertEval("where", evaluate("tensor(d0[1]):[0]"), x, y, y);
        assertEval("where", evaluate("tensor(d0[1]):[1]"), x, y, x);
        assertEval("where", evaluate("tensor(d0[1],d1[1]):[0]"), x, y, y);
        assertEval("where", evaluate("tensor(d0[1],d1[1]):[1]"), x, y, x);
    }

    @Test
    public void testCast1() throws ParseException {
        Tensor x = evaluate("tensor(d0[4]):[-1.9, 0.0, 1.1, 2.9]");
        assertEval("cast", x, evaluate("tensor(d0[4]):[1,0,1,1]"), createAttribute("to", 9));  // boolean
        assertEval("cast", x, evaluate("tensor(d0[4]):[-1,0,1,2]"), createAttribute("to", 6));  // int32
        assertEval("cast", x, evaluate("tensor(d0[4]):[-1,0,1,2]"), createAttribute("to", 12)); // uint32
        assertEval("cast", x, evaluate("tensor(d0[4]):[-1.9,0,1.1,2.9]"), createAttribute("to", 1));  // float
        try {
            assertEval("cast", x, evaluate("tensor(d0[4]):[1,0,1,1]"), createAttribute("to", 8));  // string
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "OnnxCast in cast: Casting to string is not implemented.");
        }
    }

    @Test
    public void testGather1() throws ParseException {
        // 1 dim input, 0 dim indices
        Tensor x = evaluate("tensor(d0[6]):[1,2,3,4,5,6]");
        Tensor y = evaluate("tensor():[0]");
        assertEval("gather", x, y, evaluate("tensor():[1]"));

        // 1 dim input, 1 dim indices
        x = evaluate("tensor(d0[6]):[1,2,3,4,5,6]");
        y = evaluate("tensor(d0[3]):[0,2,4]");
        assertEval("gather", x, y, evaluate("tensor(d0[3]):[1,3,5]"));

        // 2 dim input, 1 dim indices - axis 0
        x = evaluate("tensor(d0[3],d1[2]):[1, 2, 3, 4, 5, 6]");
        y = evaluate("tensor(d0[4]):[2, 1, 0, 2]");
        assertEval("gather", x, y, evaluate("tensor(d0[4],d1[2]):[5, 6, 3, 4, 1, 2, 5, 6]"));

        // 1 dim input, 2 dim indices - axis 0
        x = evaluate("tensor(d0[6]):[1, 2, 3, 4, 5, 6]");
        y = evaluate("tensor(d0[2],d1[2]):[0, 1, 3, 5]");
        assertEval("gather", x, y, evaluate("tensor(d0[2],d1[2]):[1, 2, 4, 6]"));

        // 2 dim input, 2 dim indices - axis 0
        x = evaluate("tensor(d0[3],d1[2]):[1, 2, 3, 4, 5, 6]");
        y = evaluate("tensor(d0[2],d1[2]):[0, 1, 1, 2]");
        assertEval("gather", x, y, evaluate("tensor(d0[2],d1[2],d2[2]):[1, 2, 3, 4, 3, 4, 5, 6]"), createAttribute("axis", -2));

        // 2 dim input, 1 dim indices - axis 1
        x = evaluate("tensor(d0[3],d1[2]):[1, 2, 3, 4, 5, 6]");
        y = evaluate("tensor(d0[4]):[0, 1, 0, 1]");
        assertEval("gather", x, y, evaluate("tensor(d0[3],d1[4]):[1,2,1,2,3,4,3,4,5,6,5,6]"), createAttribute("axis", 1));

        // 2 dim input, 2 dim indices - axis 1
        x = evaluate("tensor(d0[3],d1[3]):[1, 2, 3, 4, 5, 6, 7, 8, 9]");
        y = evaluate("tensor(d0[1],d1[2]):[0, 2]");
        assertEval("gather", x, y, evaluate("tensor(d0[3],d1[1],d2[2]):[1,3,4,6,7,9]"), createAttribute("axis", 1));

        // 1 dim input, 1 dim indices - negative indices
        x = evaluate("tensor(d0[6]):[1,2,3,4,5,6]");
        y = evaluate("tensor(d0[3]):[0,-2,-4]");
        assertEval("gather", x, y, evaluate("tensor(d0[3]):[1,5,3]"));
    }

    @Test
    public void testSlice1() throws ParseException {
        Tensor x = evaluate("tensor(d0[2],d1[4]):[ [1,2,3,4],[5,6,7,8] ]");
        AttributeConverter attributes = createAttributes().
                attr("starts", new int[] {1, 0}).
                attr("ends", new int[] {2, 3}).
                attr("axes", new int[] {0, 1}).build();
        assertEval("slice", x, evaluate("tensor(d0[1],d1[3]):[ [5,6,7] ]"), attributes);

        attributes = createAttributes().
                attr("starts", new int[] {0, 1}).
                attr("ends", new int[] {-1, 1000}).build();
        assertEval("slice", x, evaluate("tensor(d0[1],d1[3]):[ [2,3,4] ]"), attributes);

        attributes = createAttributes().
                attr("starts", new int[] {0, 1}).
                attr("ends", new int[] {3, 2}).
                attr("axes", new int[] {1, 0}).build();  // axes are switched
        assertEval("slice", x, evaluate("tensor(d0[1],d1[3]):[ [5,6,7] ]"), attributes);

        attributes = createAttributes().
                attr("starts", new int[] {1, 0}).
                attr("ends", new int[] {2, 3}).
                attr("axes", new int[] {0, -1}).build();  // negative axes
        assertEval("slice", x, evaluate("tensor(d0[1],d1[3]):[ [5,6,7] ]"), attributes);

        attributes = createAttributes().
                attr("starts", new int[] {1}).
                attr("ends", new int[] {2}).
                attr("axes", new int[] {0}).build();  // axis 1 is not specified
        assertEval("slice", x, evaluate("tensor(d0[1],d1[4]):[ [5,6,7,8] ]"), attributes);

        attributes = createAttributes().
                attr("starts", new int[] {0}).
                attr("ends", new int[] {1}).build();
        assertEval("slice", x, evaluate("tensor(d0[1],d1[4]):[ [1,2,3,4] ]"), attributes);
    }

    @Ignore
    @Test
    public void testSlice10() throws ParseException {
        Tensor x = evaluate("tensor(d0[2],d1[4]):[ [1,2,3,4],[5,6,7,8] ]");
        Tensor starts = evaluate("tensor(d0[2]):[1,0]");
        Tensor ends = evaluate("tensor(d0[2]):[2,3]");
        Tensor axes = evaluate("tensor(d0[2]):[0,1]");
        Tensor steps = evaluate("tensor(d0[2]):[1,2]");
        assertEval("slice", x, starts, ends, axes, steps, evaluate("tensor(d0[1],d1[2]):[ [5,7] ]"));

    }

    @Test
    public void testTranspose1() throws ParseException {
        Tensor x = evaluate("tensor(d0[2],d1[3]):[[1,2,3],[4,5,6]]");
        assertEval("transpose", x, evaluate("tensor(d0[3],d1[2]):[[1,4],[2,5],[3,6]]"));
    }

    @Test
    public void testTile6() throws ParseException {
        Tensor x = evaluate("tensor(d0[2],d1[2]):[1,2,3,4]");
        Tensor y = evaluate("tensor(d0[2]):[1,2]");
        assertEval("tile", x, y, evaluate("tensor(d0[2],d1[4]):[1,2,1,2,3,4,3,4]"));

        x = evaluate("tensor(d0[2],d1[2]):[1,2,3,4]");
        y = evaluate("tensor(d0[2]):[3,1]");
        assertEval("tile", x, y, evaluate("tensor(d0[6],d1[2]):[1,2,3,4,1,2,3,4,1,2,3,4]"));

        x = evaluate("tensor(d0[1],d1[1],d2[1]):[1]");
        y = evaluate("tensor(d0[3]):[1,6,1]");
        assertEval("tile", x, y, evaluate("tensor(d0[1],d1[6],d2[1]):[1,1,1,1,1,1]"));
    }

    @Test
    public void testSplit2() throws ParseException {
        Tensor x = evaluate("tensor(d0[6]):[1,2,3,4,5,6]");
        assertEval("split", x, evaluate("tensor(d0[3]):[1,2,3]"), 0);
        assertEval("split", x, evaluate("tensor(d0[3]):[4,5,6]"), 1);
        assertEval("split", x, evaluate("tensor(d0[2]):[1,2]"), createAttribute("split", new int[] {2}), 0);
        assertEval("split", x, evaluate("tensor(d0[4]):[3,4,5,6]"), createAttribute("split", new int[] {2}), 1);
        assertEval("split", x, evaluate("tensor(d0[3]):[3,4,5]"), createAttribute("split", new int[] {2,3}), 1);
        assertEval("split", x, evaluate("tensor(d0[1]):[6]"), createAttribute("split", new int[] {2,3}), 2);

        x = evaluate("tensor(d0[2],d1[3]):[1,2,3,4,5,6]");
        assertEval("split", x, evaluate("tensor(d0[1],d1[3]):[1,2,3]"));
        assertEval("split", x, evaluate("tensor(d0[1],d1[3]):[1,2,3]"), 0);
        assertEval("split", x, evaluate("tensor(d0[1],d1[3]):[4,5,6]"), 1);
        assertEval("split", x, evaluate("tensor(d0[1],d1[3]):[1,2,3]"), createAttribute("split", new int[] {1}), 0);
        assertEval("split", x, evaluate("tensor(d0[1],d1[3]):[4,5,6]"), createAttribute("split", new int[] {1}), 1);
        assertEval("split", x, evaluate("tensor(d0[2],d1[1]):[1,4]"), createAttribute("axis", 1), 0);
        assertEval("split", x, evaluate("tensor(d0[2],d1[1]):[2,5]"), createAttribute("axis", 1), 1);
        assertEval("split", x, evaluate("tensor(d0[2],d1[1]):[3,6]"), createAttribute("axis", 1), 2);
    }

    @Test
    public void testRange11() throws ParseException {
        Tensor start = evaluate("tensor():[3]");
        Tensor limit = evaluate("tensor():[9]");
        Tensor delta = evaluate("tensor():[3]");
        assertEval("range", start, limit, delta, evaluate("tensor(d0[2]):[3,6]"));

        start = evaluate("tensor():[10]");
        limit = evaluate("tensor():[4]");
        delta = evaluate("tensor():[-2]");
        assertEval("range", start, limit, delta, evaluate("tensor(d0[3]):[10,8,6]"));
        assertEval("range", start, limit, delta, evaluate("tensor(d0[3]):[10,8,6]"));
    }

    @Test
    public void testConstant12() throws ParseException {
        assertEval("constant", evaluate("tensor(d0[3]):[1,2,3]"), createAttribute("value", evaluate("tensor(d0[3]):[1,2,3]")));
        assertEval("constant", evaluate("tensor<float>():[313.0]"), createAttribute("value_float", 313.0f));
        assertEval("constant", evaluate("tensor():[42]"), createAttribute("value_int", 42));
    }

    @Test
    public void testConstantOfShape9() throws ParseException {
        Tensor shape = evaluate("tensor(d0[3]):[1,2,3]");
        assertEval("constantofshape", shape, evaluate("tensor(d0[1],d1[2],d2[3]):[0,0,0,0,0,0]"));
        assertEval("constantofshape", shape, evaluate("tensor<float>(d0[1],d1[2],d2[3]):[1,1,1,1,1,1]"), createAttribute("value", evaluate("tensor<float>(d0[1]):[1]")));
    }

    @Test
    public void testExpand8() throws ParseException {
        Tensor input = evaluate("tensor(d0[3],d1[1]):[1,2,3]");
        Tensor shape = evaluate("tensor(d0[2]):[3,4]");
        assertEval("expand", input, shape, evaluate("tensor(d0[3],d1[4]):[1,1,1,1,2,2,2,2,3,3,3,3]"));
        shape = evaluate("tensor(d0[3]):[2,1,4]");
        assertEval("expand", input, shape, evaluate("tensor(d0[2],d1[3],d2[4]):[1,1,1,1,2,2,2,2,3,3,3,3,1,1,1,1,2,2,2,2,3,3,3,3]"));
    }

    @Test
    public void testJoinWithSameInput() throws ParseException {
        Tensor x = evaluate("tensor(d0[2],d1[3]):[1,2,3,4,5,6]");
        String opName = "mul";

        Context context = new MapContext(DoubleValue.NaN);
        List<IntermediateOperation> inputs = new ArrayList<>();
        inputs.add(addInput(inputs, context, x, "x"));
        IntermediateOperation op = mapOperation(opName, inputs, modelName, opName, createAttributes().build(), 0);
        optimizeAndRename(opName, op);

        Tensor result = evaluate(op);
        Tensor expected = evaluate("tensor(d0[2],d1[3]):[1,4,9,16,25,36]");
        assertEquals(expected, result);
        assertEquals(expected.type(), result.type());
    }

    private Tensor evaluate(String expr) throws ParseException {
        return evaluate(expr, null, null, null);
    }

    private Tensor evaluate(String expr, Tensor x) throws ParseException {
        return evaluate(expr, x, null, null);
    }

    private Tensor evaluate(String expr, Tensor x, Tensor y) throws ParseException {
        return evaluate(expr, x, y, null);
    }

    private Tensor evaluate(String expr, Tensor x, Tensor y, Tensor z) throws ParseException {
        Context context = new MapContext(DoubleValue.NaN);
        if (x != null) context.put("x", new TensorValue(x));
        if (y != null) context.put("y", new TensorValue(y));
        if (z != null) context.put("z", new TensorValue(z));
        return new RankingExpression(expr).evaluate(context).asTensor();
    }

    private Tensor evaluate(IntermediateOperation op) {
        Tensor tensor = op.evaluateAsConstant(op.type().get()).asTensor();
        return renameToStandardType(op, tensor);
    }

    private void assertEval(String opName, Tensor expected, AttributeConverter attr) {
        assertEval(opName, null, null, null, null, null, expected, attr, 0);
    }

    private void assertEval(String opName, Tensor x, Tensor expected) {
        assertEval(opName, x, null, null, null, null, expected, null, 0);
    }

    private void assertEval(String opName, Tensor x, Tensor expected, int output) {
        assertEval(opName, x, null, null, null, null, expected, null, output);
    }

    private void assertEval(String opName, Tensor x, Tensor expected, AttributeConverter attr) {
        assertEval(opName, x, null, null, null, null, expected, attr, 0);
    }

    private void assertEval(String opName, Tensor x, Tensor expected, AttributeConverter attr, int output) {
        assertEval(opName, x, null, null, null, null, expected, attr, output);
    }

    private void assertEval(String opName, Tensor x, Tensor y, Tensor expected, AttributeConverter attr) {
        assertEval(opName, x, y, null, null, null, expected, attr, 0);
    }

    private void assertEval(String opName, Tensor x, Tensor y, Tensor expected) {
        assertEval(opName, x, y, null, null, null, expected, null, 0);
    }

    private void assertEval(String opName, Tensor x, Tensor y, Tensor z, Tensor expected) {
        assertEval(opName, x, y, z, null, null, expected, null, 0);
    }

    private void assertEval(String opName, Tensor x, Tensor y, Tensor z, Tensor expected, AttributeConverter attr) {
        assertEval(opName, x, y, z, null, null, expected, attr, 0);
    }

    private void assertEval(String opName, Tensor x, Tensor y, Tensor z, Tensor q, Tensor expected) {
        assertEval(opName, x, y, z, q, null, expected, null, 0);
    }

    private void assertEval(String opName, Tensor x, Tensor y, Tensor z, Tensor q, Tensor r, Tensor expected) {
        assertEval(opName, x, y, z, q, r, expected, null, 0);
    }

    private void assertEval(String opName, Tensor x, Tensor y, Tensor z, Tensor q, Tensor r, Tensor expected, AttributeConverter attr, int output) {
        Context context = new MapContext(DoubleValue.NaN);
        List<IntermediateOperation> inputs = createInputs(context, x, y, z, q, r);
        IntermediateOperation op = mapOperation(opName, inputs, modelName, opName, attr != null ? attr : createAttributes().build(), output);
        optimizeAndRename(opName, op);
        Tensor result = evaluate(op);
        assertEquals(expected, result);
        assertEquals(expected.type(), result.type());
    }

    private List<IntermediateOperation> createInputs(Context context, Tensor x, Tensor y, Tensor z, Tensor q, Tensor r) {
        List<IntermediateOperation> inputs = new ArrayList<>();
        addInput(inputs, context, x, "x");
        addInput(inputs, context, y, "y");
        addInput(inputs, context, z, "z");
        addInput(inputs, context, q, "q");
        addInput(inputs, context, r, "r");
        return inputs;
    }

    private IntermediateOperation addInput(List<IntermediateOperation> inputs, Context context, Tensor x, String name) {
        if (x == null) return null;
        context.put(name, new TensorValue(x));
        IntermediateOperation op = new Constant(modelName, name, OrderedTensorType.fromSpec(x.type().toString()));
        op.setConstantValueFunction(type -> new TensorValue(convertTypeAfterRename(x, type)));
        inputs.add(op);
        return op;
    }

    Tensor convertTypeAfterRename(Tensor tensor, OrderedTensorType type) {
        IndexedTensor indexedTensor = (IndexedTensor) tensor;
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(type.type());
        for (int i = 0; i < indexedTensor.size(); i++) {
            builder.cellByDirectIndex(type.toDirectIndex(i), indexedTensor.get(i));
        }
        return builder.build();
    }

    private TensorFunction<Reference> optimizeAndRename(String opName, IntermediateOperation op) {
        IntermediateGraph graph = new IntermediateGraph(modelName);
        graph.put(opName, op);
        graph.outputs(graph.defaultSignature()).put(opName, opName);
        graph.optimize();
        return op.function().get();
    }

    private Tensor renameToStandardType(IntermediateOperation op, Tensor tensor) {
        OrderedTensorType operationType = op.type().get();
        OrderedTensorType standardNamingType = OrderedTensorType.standardType(operationType);
        if ( ! operationType.equals(standardNamingType)) {
            List<String> renameFrom = operationType.dimensionNames();
            List<String> renameTo = standardNamingType.dimensionNames();
            TensorFunction<Reference> func = new Rename<>(new ConstantTensor<Reference>(tensor), renameFrom, renameTo);
            return func.evaluate();
        }
        return tensor;
    }

    static AttributeConverter createAttribute(String name, int val) {
        return new Attributes().attr(name, val).build();
    }

    static AttributeConverter createAttribute(String name, float val) {
        return new Attributes().attr(name, val).build();
    }

    static AttributeConverter createAttribute(String name, int [] vals) {
        return new Attributes().attr(name, vals).build();
    }

    static AttributeConverter createAttribute(String name, Tensor val) {
        return new Attributes().attr(name, val).build();
    }

    static Attributes createAttributes() {
        return new Attributes();
    }

    private static class Attributes {

        Onnx.NodeProto.Builder nodeBuilder;

        Attributes() {
            this.nodeBuilder = Onnx.NodeProto.newBuilder();
        }

        Attributes attr(String name, int val) {
            nodeBuilder.addAttribute(Onnx.AttributeProto.newBuilder().setName(name).setType(INT).setI(val).build());
            return this;
        }

        Attributes attr(String name, float val) {
            nodeBuilder.addAttribute(Onnx.AttributeProto.newBuilder().setName(name).setType(FLOAT).setF(val).build());
            return this;
        }

        Attributes attr(String name, int [] vals) {
            Onnx.AttributeProto.Builder builder = Onnx.AttributeProto.newBuilder();
            for (int val : vals) {
                builder.addInts(val);
            }
            nodeBuilder.addAttribute(builder.setName(name).setType(INTS).build());
            return this;
        }

        Attributes attr(String name, Tensor tensor) {
            Onnx.TensorProto.Builder builder = Onnx.TensorProto.newBuilder();
            tensor.type().dimensions().forEach(d -> builder.addDims(d.size().get()));
            if (tensor.type().valueType() == TensorType.Value.FLOAT) {
                builder.setDataType(Onnx.TensorProto.DataType.FLOAT);
                tensor.valueIterator().forEachRemaining(d -> builder.addFloatData(d.floatValue()));
            } else {
                builder.setDataType(Onnx.TensorProto.DataType.DOUBLE);
                tensor.valueIterator().forEachRemaining(builder::addDoubleData);
            }
            Onnx.TensorProto val = builder.build();
            nodeBuilder.addAttribute(Onnx.AttributeProto.newBuilder().setName(name).setType(TENSOR).setT(val).build());
            return this;
        }

        AttributeConverter build() {
            return AttributeConverter.convert(nodeBuilder.build());
        }

    }

}
