// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.evaluation.MapEvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.ConstantTensor;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.TensorFunction;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.stream.Collectors;

import static com.yahoo.tensor.TensorType.Dimension.Type;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests tensor functionality
 *
 * @author bratseth
 */
public class TensorTestCase {

    @Test
    public void testFactory() {
        assertTrue(Tensor.from("tensor():{5.7}") instanceof IndexedTensor);
    }

    @Test
    public void testToString() {
        assertEquals("tensor():{5.7}", Tensor.from("{5.7}").toString());
        assertEquals("tensor(x[3]):[0.1, 0.2, 0.3]",
                     Tensor.from("tensor(x[3]):[0.1, 0.2, 0.3]").toString());
        assertEquals("tensor(d1{},d2{}):{{d1:l1,d2:l1}:5.0, {d1:l1,d2:l2}:6.0}",
                     Tensor.from("{ {d1:l1,d2:l1}: 5,   {d2:l2, d1:l1}:6.0} ").toString());
        assertEquals("tensor(d1{},d2{}):{{d1:l1,d2:l1}:-5.3, {d1:l1,d2:l2}:0.0}",
                     Tensor.from("{ {d1:l1,d2:l1}:-5.3, {d2:l2, d1:l1}:0}").toString());
        assertEquals("tensor(m{},x[3]):{k1:[0.0, 1.0, 2.0], k2:[0.0, 1.0, 2.0], k3:[0.0, 1.0, 2.0], k4:[0.0, 1.0, 2.0]}",
                     Tensor.from("tensor(m{},x[3]):{k1:[0,1,2], k2:[0,1,2], k3:[0,1,2], k4:[0,1,2]}").toString());
        assertEquals("tensor(m{},n{},x[3]):" +
                     "{{m:k1,n:k1,x:0}:0.0, {m:k1,n:k1,x:1}:1.0, {m:k1,n:k1,x:2}:2.0," +
                     " {m:k2,n:k1,x:0}:0.0, {m:k2,n:k1,x:1}:1.0, {m:k2,n:k1,x:2}:2.0," +
                     " {m:k3,n:k1,x:0}:0.0, {m:k3,n:k1,x:1}:1.0, {m:k3,n:k1,x:2}:2.0}",
                     Tensor.from("tensor(m{},n{},x[3]):" +
                                 "{{m:k1,n:k1,x:0}:0, {m:k1,n:k1,x:1}:1, {m:k1,n:k1,x:2}:2, " +
                                 " {m:k2,n:k1,x:0}:0, {m:k2,n:k1,x:1}:1, {m:k2,n:k1,x:2}:2, " +
                                 " {m:k3,n:k1,x:0}:0, {m:k3,n:k1,x:1}:1, {m:k3,n:k1,x:2}:2}").toString());
        assertEquals("tensor(m{},x[2],y[2]):" +
                     "{k1:[[0.0, 1.0], [2.0, 3.0]], k2:[[0.0, 1.0], [2.0, 3.0]], k3:[[0.0, 1.0], [2.0, 3.0]]}",
                     Tensor.from("tensor(m{},x[2],y[2]):{k1:[[0,1],[2,3]], k2:[[0,1],[2,3]], k3:[[0,1],[2,3]]}").toString());
        assertEquals("Labels are quoted when necessary",
                     "tensor(d1{}):{\"'''\":6.0, '[[\":\"]]':5.0}",
                     Tensor.from("{ {d1:'[[\":\"]]'}: 5, {d1:\"'''\"}:6.0 }").toString());
    }

    @Test
    public void testToAbbreviatedString() {
        assertEquals("tensor(x[10]):[0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0]",
                     Tensor.from("tensor(x[10]):[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]").toAbbreviatedString());
        assertEquals("[0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0]",
                     Tensor.from("tensor(x[10]):[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]").toAbbreviatedString(false, true));
        assertEquals("tensor(x[14]):[0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, ...]",
                     Tensor.from("tensor(x[14]):[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]").toAbbreviatedString());
        assertEquals("tensor(d1{},d2{}):{{d1:l1,d2:l1}:6.0, {d1:l1,d2:l2}:6.0, {d1:l1,d2:l3}:6.0, ...}",
                     Tensor.from("{{d1:l1,d2:l1}:6, {d2:l2,d1:l1}:6, {d2:l3,d1:l1}:6, {d2:l4,d1:l1}:6, {d2:l5,d1:l1}:6," +
                                 " {d2:l6,d1:l1}:6, {d2:l7,d1:l1}:6, {d2:l8,d1:l1}:6, {d2:l9,d1:l1}:6, {d2:l2,d1:l2}:6," +
                                 " {d2:l2,d1:l3}:6, {d2:l2,d1:l4}:6}").toAbbreviatedString());
        assertEquals("tensor(m{},x[3]):{k1:[0.0, 1.0, 2.0], k2:[0.0, 1.0, ...}",
                     Tensor.from("tensor(m{},x[3]):{k1:[0,1,2], k2:[0,1,2], k3:[0,1,2], k4:[0,1,2]}").toAbbreviatedString());
        assertEquals("tensor(m{},x[3]):{k1:[0.0, 1.0, 2.0], k2:[0.0, 1.0, ...}",
                     Tensor.from("tensor(m{},x[3]):{k1:[0,1,2], k2:[0,1,2], k3:[0,1,2], k4:[0,1,2]}").toAbbreviatedString());
        assertEquals("tensor(m{},n{},x[3]):{{m:k1,n:k1,x:0}:0.0, {m:k1,n:k1,x:1}:1.0, {m:k1,n:k1,x:2}:2.0, ...}",
                     Tensor.from("tensor(m{},n{},x[3]):" +
                                 "{{m:k1,n:k1,x:0}:0, {m:k1,n:k1,x:1}:1, {m:k1,n:k1,x:2}:2, " +
                                 " {m:k2,n:k1,x:0}:0, {m:k2,n:k1,x:1}:1, {m:k2,n:k1,x:2}:2, " +
                                 " {m:k3,n:k1,x:0}:0, {m:k3,n:k1,x:1}:1, {m:k3,n:k1,x:2}:2}").toAbbreviatedString());
        assertEquals("tensor(m{},x[2],y[2]):{k1:[[0.0, 1.0], [2.0, 3.0]], k2:[[0.0, ...}",
                     Tensor.from("tensor(m{},x[2],y[2]):{k1:[[0,1],[2,3]], k2:[[0,1],[2,3]], k3:[[0,1],[2,3]]}").toAbbreviatedString());
        assertEquals("{k1:[[0.0, 1.0], [2.0, 3.0]], k2:[[0.0, ...}",
                     Tensor.from("tensor(m{},x[2],y[2]):{k1:[[0,1],[2,3]], k2:[[0,1],[2,3]], k3:[[0,1],[2,3]]}").toAbbreviatedString(false, true));
        assertEquals("{{m:k1,x:0,y:0}:0.0, {m:k1,x:0,y:1}:1.0, {m:k1,x:1,y:0}:2.0, {m:k1,x:1,y:1}:3.0, {m:k2,x:0,y:0}:0.0, ...}",
                     Tensor.from("tensor(m{},x[2],y[2]):{k1:[[0,1],[2,3]], k2:[[0,1],[2,3]], k3:[[0,1],[2,3]]}").toAbbreviatedString(false, false));
    }

    @Test
    public void testValueTypes() {
        assertEquals(Tensor.from("tensor<double>(x[1]):{{x:0}:5}").getClass(), IndexedDoubleTensor.class);
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor<double>(x[1])")).cell(5.0, 0).build().getClass(),
                     IndexedDoubleTensor.class);

        assertEquals(Tensor.from("tensor<float>(x[1]):{{x:0}:5}").getClass(), IndexedFloatTensor.class);
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor<float>(x[1])")).cell(5.0, 0).build().getClass(),
                     IndexedFloatTensor.class);

        assertEquals(Tensor.from("tensor<bfloat16>(x[1]):[5]").getClass(), IndexedFloatTensor.class);
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor<bfloat16>(x[1])")).cell(5.0, 0).build().getClass(),
                IndexedFloatTensor.class);

        assertEquals(Tensor.from("tensor<int8>(x[1]):[5]").getClass(), IndexedFloatTensor.class);
        assertEquals(Tensor.Builder.of(TensorType.fromSpec("tensor<int8>(x[1])")).cell(5.0, 0).build().getClass(),
                IndexedFloatTensor.class);
    }

    @Test
    public void testValueTypeResolving() {
        assertCellTypeResult(TensorType.Value.DOUBLE, "double", "double");
        assertCellTypeResult(TensorType.Value.DOUBLE, "double", "float");
        assertCellTypeResult(TensorType.Value.FLOAT, "float", "float");
        // Test bfloat16 and int8 when we have proper cell type resolving in place.
    }

    @Test
    public void testParseError() {
        try {
            Tensor.from("--");
            fail("Expected parse error");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Excepted a number or a string starting by {, [ or tensor(...):, got '--'",
                         expected.getCause().getMessage());
        }
    }

    @Test
    public void testDimensions() {
        Set<String> dimensions1 = Tensor.from("{} ").type().dimensionNames();
        assertEquals(0, dimensions1.size());

        Set<String> dimensions2 = Tensor.from("{ {d1:l1, d2:l2}:5, {d1:l2, d2:l2}:6.0} ").type().dimensionNames();
        assertEquals(2, dimensions2.size());
        assertTrue(dimensions2.contains("d1"));
        assertTrue(dimensions2.contains("d2"));

        Set<String> dimensions3 = Tensor.from("{ {d1:l1, d2:l1, d3:l1}:5, {d1:l1, d2:l2, d3:l1}:6.0} ").type().dimensionNames();
        assertEquals(3, dimensions3.size());
        assertTrue(dimensions3.contains("d1"));
        assertTrue(dimensions3.contains("d2"));
        assertTrue(dimensions3.contains("d3"));
    }

    @Test
    public void testExpressions() {
        Tensor y =  Tensor.from("{{y:1}:3}");
        Tensor x =  Tensor.from("{{x:0}:5,{x:1}:7}");
        Tensor xy = Tensor.from("{{x:0,y:1}:11, {x:1,y:1}:13}");
        double nest1 = y.multiply(x.multiply(xy).sum("x")).sum("y").asDouble();
        double nest2 = x.multiply(xy).sum("x").multiply(y).sum("y").asDouble();
        double flat = y.multiply(x).multiply(xy).sum(ImmutableList.of("x","y")).asDouble();
        assertEquals(nest1, flat, 0.000000001);
        assertEquals(nest2, flat, 0.000000001);
    }

    @Test
    public void testCombineInDimensionIndexed() {
        Tensor input =  Tensor.from("tensor(input[2]):{{input:0}:3, {input:1}:7}");
        Tensor result = input.concat(11, "input");
        assertEquals("tensor(input[3]):[3.0, 7.0, 11.0]", result.toString());
    }

    /** All functions are more throughly tested in searchlib EvaluationTestCase */
    @Test
    public void testTensorComputation() {
        Tensor tensor1 = Tensor.from("{ {x:1}:3, {x:2}:7 }");
        Tensor tensor2 = Tensor.from("{ {y:1}:5 }");
        assertEquals(Tensor.from("{ {x:1,y:1}:15, {x:2,y:1}:35 }"), tensor1.multiply(tensor2));
        assertEquals(Tensor.from("{ {x:1,y:1}:12, {x:2,y:1}:28 }"), tensor1.join(tensor2, (a, b) -> a * b - a ));
        assertEquals(Tensor.from("{ {x:1,y:1}:0, {x:2,y:1}:1 }"), tensor1.larger(tensor2));
        assertEquals(Tensor.from("{ {y:1}:50.0 }"), tensor1.matmul(tensor2, "x"));
        assertEquals(Tensor.from("{ {z:1}:3, {z:2}:7 }"), tensor1.rename("x", "z"));
        assertEquals(Tensor.from("{ {y:1,x:1}:8, {x:1,y:2}:12 }"), tensor1.add(tensor2).rename(ImmutableList.of("x", "y"),
                                                                                               ImmutableList.of("y", "x")));
        assertEquals(Tensor.from("{ {x:0,y:0}:0, {x:0,y:1}:0, {x:1,y:0}:0, {x:1,y:1}:1, {x:2,y:0}:0, {x:2,y:1}:2, }"),
                     Tensor.generate(new TensorType.Builder().indexed("x", 3).indexed("y", 2).build(),
                                     (List<Long> indexes) -> (double)indexes.get(0)*indexes.get(1)));
        assertEquals(Tensor.from("{ {x:0,y:0,z:0}:0, {x:0,y:1,z:0}:1, {x:1,y:0,z:0}:1, {x:1,y:1,z:0}:2, {x:2,y:0,z:0}:2, {x:2,y:1,z:0}:3, "+
                                 "  {x:0,y:0,z:1}:1, {x:0,y:1,z:1}:2, {x:1,y:0,z:1}:2, {x:1,y:1,z:1}:3, {x:2,y:0,z:1}:3, {x:2,y:1,z:1}:4 }"),
                     Tensor.range(new TensorType.Builder().indexed("x", 3).indexed("y", 2).indexed("z", 2).build()));
        assertEquals(Tensor.from("{ {x:0,y:0,z:0}:1, {x:0,y:1,z:0}:0, {x:1,y:0,z:0}:0, {x:1,y:1,z:0}:0, {x:2,y:0,z:0}:0, {x:2,y:1,z:0}:0, "+
                                 "  {x:0,y:0,z:1}:0, {x:0,y:1,z:1}:0, {x:1,y:0,z:1}:0, {x:1,y:1,z:1}:1, {x:2,y:0,z:1}:0, {x:2,y:1,z:1}:00 }  "),
                     Tensor.diag(new TensorType.Builder().indexed("x", 3).indexed("y", 2).indexed("z", 2).build()));
        assertEquals(Tensor.from("{ {x:1}:0, {x:3}:1, {x:9}:0 }"), Tensor.from("{ {x:1}:1, {x:3}:5, {x:9}:3 }").argmax("x"));
    }

    /** Test the same computation made in various ways which are implemented with special-case optimizations */
    @Test
    public void testOptimizedComputation() {
        assertEquals("Mapped vector",          42, (int)dotProduct(vector(Type.mapped), vectors(Type.mapped, 2)));
        assertEquals("Indexed unbound vector", 42, (int)dotProduct(vector(3, Type.indexedUnbound), vectors(5, Type.indexedUnbound, 2)));
        assertEquals("Indexed unbound vector", 42, (int)dotProduct(vector(5, Type.indexedUnbound), vectors(3, Type.indexedUnbound, 2)));
        assertEquals("Indexed bound vector",   42, (int)dotProduct(vector(3, Type.indexedBound), vectors(3, Type.indexedBound, 2)));
        assertEquals("Mapped matrix",          42, (int)dotProduct(vector(Type.mapped), matrix(Type.mapped, 2)));
        assertEquals("Indexed unbound matrix", 42, (int)dotProduct(vector(3, Type.indexedUnbound), matrix(5, Type.indexedUnbound, 2)));
        assertEquals("Indexed unbound matrix", 42, (int)dotProduct(vector(5, Type.indexedUnbound), matrix(3, Type.indexedUnbound, 2)));
        assertEquals("Indexed bound matrix",   42, (int)dotProduct(vector(3, Type.indexedBound), matrix(3, Type.indexedBound, 2)));
        assertEquals("Mixed vector",           42, (int)dotProduct(vector(Type.mapped), vectors(Type.indexedUnbound, 2)));
        assertEquals("Mixed vector",           42, (int)dotProduct(vector(Type.mapped), vectors(Type.indexedUnbound, 2)));
        assertEquals("Mixed matrix",           42, (int)dotProduct(vector(Type.mapped), matrix(Type.indexedUnbound, 2)));
        assertEquals("Mixed matrix",           42, (int)dotProduct(vector(Type.mapped), matrix(Type.indexedUnbound, 2)));
        assertEquals("Mixed vector",           42, (int)dotProduct(vector(Type.indexedUnbound), vectors(Type.mapped, 2)));
        assertEquals("Mixed vector",           42, (int)dotProduct(vector(Type.indexedUnbound), vectors(Type.mapped, 2)));
        assertEquals("Mixed matrix",           42, (int)dotProduct(vector(Type.indexedUnbound), matrix(Type.mapped, 2)));
        assertEquals("Mixed matrix",           42, (int)dotProduct(vector(Type.indexedUnbound), matrix(Type.mapped, 2)));

        // Test the unoptimized path by joining in another dimension
        Tensor unitJ = Tensor.Builder.of(new TensorType.Builder().mapped("j").build()).cell().label("j", 0).value(1).build();
        Tensor unitK = Tensor.Builder.of(new TensorType.Builder().mapped("k").build()).cell().label("k", 0).value(1).build();
        Tensor vectorInJSpace = vector(Type.mapped).multiply(unitJ);
        Tensor matrixInKSpace = matrix(Type.mapped, 2).get(0).multiply(unitK);
        assertEquals("Generic computation implementation", 42, (int)dotProduct(vectorInJSpace, Collections.singletonList(matrixInKSpace)));
    }

    @Test
    public void testTensorModify() {
        assertTensorModify((left, right) -> right,
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1, {x:0,y:1}:2}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:1}:0}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1,{x:0,y:1}:0}"));
        assertTensorModify((left, right) -> left + right,
                Tensor.from("tensor(x[1],y[2])", "{{x:0,y:0}:1, {x:0,y:1}:2}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:1}:3}"),
                Tensor.from("tensor(x[1],y[2])", "{{x:0,y:0}:1,{x:0,y:1}:5}"));
        assertTensorModify((left, right) -> left * right,
                Tensor.from("tensor(x[1],y[2])", "{{x:0,y:0}:1, {x:0,y:1}:2}"),
                Tensor.from("tensor(x[1],y[3])", "{}"),
                Tensor.from("tensor(x[1],y[2])", "{{x:0,y:0}:0,{x:0,y:1}:0}"));
        assertTensorModify((left, right) -> left * right,
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:1, {x:0,y:1}:2}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:1}:3}"),
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:1,{x:0,y:1}:6}"));
    }

    @Test
    public void testTensorMerge() {
        assertTensorMerge(
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1,{x:0,y:1}:2}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:2}:3}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1,{x:0,y:1}:2,{x:0,y:2}:3}"));
        assertTensorMerge(
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1,{x:0,y:1}:2}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:1}:3}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1,{x:0,y:1}:3}"));
        assertTensorMerge(
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1,{x:0,y:1}:2}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:1}:3,{x:0,y:2}:4}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1,{x:0,y:1}:3,{x:0,y:2}:4}"));
        assertTensorMerge(
                Tensor.from("tensor(x{},y{})", "{}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:5}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:5}"));
        assertTensorMerge(
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1,{x:0,y:1}:2}"),
                Tensor.from("tensor(x{},y{})", "{}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1,{x:0,y:1}:2}"));
        assertTensorMerge(
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:1,{x:0,y:1}:2}"),
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:2}:3}"),
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:0,{x:0,y:1}:0,{x:0,y:2}:3}"));  // notice difference with sparse case - y is dense dimension here with default value 0.0
        assertTensorMerge(
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:1,{x:0,y:1}:2}"),
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:1}:3}"),
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:0,{x:0,y:1}:3,{x:0,y:2}:0}"));
        assertTensorMerge(
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:1,{x:0,y:1}:2}"),
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:1}:3,{x:0,y:2}:4}"),
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:0,{x:0,y:1}:3,{x:0,y:2}:4}"));
        assertTensorMerge(
                Tensor.from("tensor(x{},y[3])", "{}"),
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:5}"),
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:5}"));
        assertTensorMerge(
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:1,{x:0,y:1}:2}"),
                Tensor.from("tensor(x{},y[3])", "{}"),
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:1,{x:0,y:1}:2}"));
        assertTensorMerge(
                Tensor.from("tensor(x[4]):[5,6,7,8]"),
                Tensor.from("tensor(x[4]):[1,2,3,4]"),
                Tensor.from("tensor(x[4]):[1,2,3,4]"));
        assertTensorMerge(
                Tensor.from("tensor(x[]):{{x:0}:1,{x:1}:2,{x:2}:3,{x:3}:4}"),
                Tensor.from("tensor(x[]):{{x:0}:5,{x:1}:6}"),
                Tensor.from("tensor(x[4]):[5,6,3,4]"));
        assertTensorMerge(
                Tensor.from("tensor(x{}):{a:1,b:2}"),
                Tensor.from("tensor(x{}):{b:3,c:4}"),
                Tensor.from("tensor(x{}):{a:1,b:3,c:4}"));
        assertTensorMerge(
                Tensor.from("tensor(key{},x[4]):{a:[1,2,3,4],c:[5,6,7,8]}"),
                Tensor.from("tensor(key{},x[4]):{a:[9,10,11,12],b:[13,14,15,16]}"),
                Tensor.from("tensor(key{},x[4]):{a:[9,10,11,12],b:[13,14,15,16],c:[5,6,7,8]}"));
    }

    @Test
    public void testTensorRemove() {
        assertTensorRemove(
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:2,{x:0,y:1}:3}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:1}:1}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:2}"));
        assertTensorRemove(
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1,{x:0,y:1}:2}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1,{x:0,y:1}:1}"),
                Tensor.from("tensor(x{},y{})", "{}"));
        assertTensorRemove(
                Tensor.from("tensor(x{},y{})", "{}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:1}"),
                Tensor.from("tensor(x{},y{})", "{}"));
        assertTensorRemove(
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:2,{x:0,y:1}:3}"),
                Tensor.from("tensor(x{},y{})", "{}"),
                Tensor.from("tensor(x{},y{})", "{{x:0,y:0}:2,{x:0,y:1}:3}"));
        assertTensorRemove(
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:2, {x:0,y:1}:3}"),
                Tensor.from("tensor(x{})", "{{x:0}:1}"),  // notice update is without dense dimension
                Tensor.from("tensor(x{},y[3])", "{}"));
        assertTensorRemove(
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:1,{x:1,y:0}:2}"),
                Tensor.from("tensor(x{})", "{{x:0}:1}"),
                Tensor.from("tensor(x{},y[3])", "{{x:1,y:0}:2,{x:1,y:1}:0,{x:1,y:2}:0}"));
        assertTensorRemove(
                Tensor.from("tensor(x{},y[3])", "{}"),
                Tensor.from("tensor(x{})", "{{x:0}:1}"),
                Tensor.from("tensor(x{},y[3])", "{}"));
        assertTensorRemove(
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:2,{x:0,y:1}:3}"),
                Tensor.from("tensor(x{})", "{}"),
                Tensor.from("tensor(x{},y[3])", "{{x:0,y:0}:2,{x:0,y:1}:3}"));
    }

    @Test
    public void testLargest() {
        assertLargest("{d1:l1,d2:l2}:6.0",
                     "tensor(d1{},d2{}):{{d1:l1,d2:l1}:5.0,{d1:l1,d2:l2}:6.0}");
        assertLargest("{d1:l1,d2:l1}:6.0, {d1:l1,d2:l2}:6.0",
                      "tensor(d1{},d2{}):{{d1:l1,d2:l1}:6.0,{d1:l1,d2:l2}:6.0}");
        assertLargest("{d1:l1,d2:l1}:6.0, {d1:l1,d2:l2}:6.0",
                      "tensor(d1{},d2{}):{{d1:l1,d2:l1}:6.0,{d1:l1,d2:l3}:5.0,{d1:l1,d2:l2}:6.0}");
        assertLargest("{x:1,y:1}:4.0",
                      "tensor(x[2],y[2]):[[1,2],[3,4]]");
        assertLargest("{x:0,y:0}:4.0, {x:1,y:1}:4.0",
                      "tensor(x[2],y[2]):[[4,2],[3,4]]");
    }

    @Test
    public void testSmallest() {
        assertSmallest("{d1:l1,d2:l1}:5.0",
                       "tensor(d1{},d2{}):{{d1:l1,d2:l1}:5.0,{d1:l1,d2:l2}:6.0}");
        assertSmallest("{d1:l1,d2:l1}:6.0, {d1:l1,d2:l2}:6.0",
                       "tensor(d1{},d2{}):{{d1:l1,d2:l1}:6.0,{d1:l1,d2:l2}:6.0}");
        assertSmallest("{d1:l1,d2:l1}:5.0, {d1:l1,d2:l2}:5.0",
                       "tensor(d1{},d2{}):{{d1:l1,d2:l1}:5.0,{d1:l1,d2:l3}:6.0,{d1:l1,d2:l2}:5.0}");
        assertSmallest("{x:0,y:0}:1.0",
                       "tensor(x[2],y[2]):[[1,2],[3,4]]");
        assertSmallest("{x:0,y:1}:2.0",
                       "tensor(x[2],y[2]):[[4,2],[3,4]]");
    }

    private void assertCellTypeResult(TensorType.Value valueType, String type1, String type2) {
        Tensor t1 = Tensor.from("tensor<" + type1 + ">(x[1]):[3] }");
        Tensor t2 = Tensor.from("tensor<" + type2 + ">(x[1]):[5] }");
        assertEquals(valueType, t1.multiply(t2).type().valueType());
        assertEquals(valueType, t2.multiply(t1).type().valueType());
    }

    private void assertLargest(String expectedCells, String tensorString) {
        Tensor tensor = Tensor.from(tensorString);
        assertEquals(expectedCells, asString(tensor.largest(), tensor.type()));
    }

    private void assertSmallest(String expectedCells, String tensorString) {
        Tensor tensor = Tensor.from(tensorString);
        assertEquals(expectedCells, asString(tensor.smallest(), tensor.type()));
    }

    private String asString(List<Tensor.Cell> cells, TensorType type) {
        return cells.stream().map(cell -> cell.toString(type)).collect(Collectors.joining(", "));
    }

    private void assertTensorModify(DoubleBinaryOperator op, Tensor init, Tensor update, Tensor expected) {
        assertEquals(expected, init.modify(op, update.cells()));
    }

    private void assertTensorMerge(Tensor init, Tensor update, Tensor expected) {
        DoubleBinaryOperator op = (left, right) -> right;
        assertEquals(expected, init.merge(update, op));
    }

    private void assertTensorRemove(Tensor init, Tensor update, Tensor expected) {
        assertEquals(expected, init.remove(update.cells().keySet()));
    }


    private double dotProduct(Tensor tensor, List<Tensor> tensors) {
        double sum = 0;
        TensorFunction<Name> dotProductFunction = new Reduce<>(new Join<>(new ConstantTensor<>(tensor),
                                                                          new VariableTensor<>("argument"), (a, b) -> a * b),
                                                               Reduce.Aggregator.sum).toPrimitive();
        MapEvaluationContext<Name> context = new MapEvaluationContext<>();

        for (Tensor tensorElement : tensors) { // tensors.size() = 1 for larger tensor
            context.put("argument", tensorElement);
            // System.out.println("Dot product of " + tensor + " and " + tensorElement + ": " + dotProductFunction.evaluate(context).asDouble());
            sum += dotProductFunction.evaluate(context).asDouble();
        }
        return sum;
    }

    private Tensor vector(TensorType.Dimension.Type dimensionType) {
        return vectors(dimensionType, 1).get(0);
    }

    private Tensor vector(int vectorSize, TensorType.Dimension.Type dimensionType) {
        return vectors(vectorSize, dimensionType, 1).get(0);
    }

    /** Create a list of vectors having a single dimension x */
    private List<Tensor> vectors(TensorType.Dimension.Type dimensionType, int vectorCount) {
        return vectors(3, dimensionType, vectorCount);
    }

    private List<Tensor> vectors(int vectorSize, TensorType.Dimension.Type dimensionType, int vectorCount) {
        List<Tensor> tensors = new ArrayList<>();
        TensorType type = vectorType(new TensorType.Builder(), "x", dimensionType, vectorSize);
        for (int i = 0; i < vectorCount; i++) {
            Tensor.Builder builder = Tensor.Builder.of(type);
            for (int j = 0; j < vectorSize; j++) {
                builder.cell().label("x", String.valueOf(j)).value((i+1)*(j+1));
            }
            tensors.add(builder.build());
        }
        return tensors;
    }

    /**
     * Create a matrix of vectors (in dimension i) where each vector has the dimension x.
     * This matrix contains the same vectors as returned by createVectors, in a single list element for convenience.
     */
    private List<Tensor> matrix(TensorType.Dimension.Type dimensionType, int vectorCount) {
        return matrix(3, dimensionType, vectorCount);
    }

    private List<Tensor> matrix(int vectorSize, TensorType.Dimension.Type dimensionType, int vectorCount) {
        TensorType.Builder typeBuilder = new TensorType.Builder();
        typeBuilder.dimension("i", dimensionType == Type.indexedBound ? Type.indexedUnbound : dimensionType);
        vectorType(typeBuilder, "x", dimensionType, vectorSize);
        Tensor.Builder builder = Tensor.Builder.of(typeBuilder.build());
        for (int i = 0; i < vectorCount; i++) {
            for (int j = 0; j < vectorSize; j++) {
                builder.cell()
                        .label("i", String.valueOf(i))
                        .label("x", String.valueOf(j))
                        .value((i+1)*(j+1));
            }
        }
        return Collections.singletonList(builder.build());
    }

    private TensorType vectorType(TensorType.Builder builder, String name, TensorType.Dimension.Type type, int size) {
        switch (type) {
            case mapped: builder.mapped(name); break;
            case indexedUnbound: builder.indexed(name); break;
            case indexedBound: builder.indexed(name, size); break;
            default: throw new IllegalArgumentException("Dimension type " + type + " not supported");
        }
        return builder.build();
    }

}
