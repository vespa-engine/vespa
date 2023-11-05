// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.evaluation.VariableTensor;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author arnej
 */
public class MapSubspacesTestCase {

    static class MyCellGenSumNext implements ScalarFunction<Name> {
        @Override public Double apply(EvaluationContext<Name> context) {
            Tensor input = context.getTensor("denseInput");
            long dimIdx = (long) context.getTensor("x").asDouble();
            var addrA = TensorAddress.of(dimIdx);
            var addrB = TensorAddress.of(dimIdx + 1);
            double value = input.get(addrA) + input.get(addrB);
            return value;
        }
    }

    private static Tensor map3to2(Tensor input, String cellType) {
        TensorType tt = TensorType.fromSpec("tensor<" + cellType + ">(x[2])");
        var tfun = Generate.<Name>bound(tt, new MyCellGenSumNext());
        var constInput = new ConstantTensor<Name>(input);
        var mapper = new MapSubspaces<Name>(constInput, "denseInput", tfun);
        Tensor mapped = mapper.evaluate();
        System.err.println("Mapped 3->2: " + mapped);
        return mapped;
    }

    private static void checkResult(Tensor expect, Tensor result, TensorType.Value cellType) {
        Tensor withType = expect.cellCast(cellType);
        assertEquals(withType, result);
        assertEquals(cellType, result.type().valueType());
    }

    @Test
    public void testBasicMap() {
        Tensor t1, t2;
        t1 = Tensor.from("tensor(a{},x[3]):{foo:[1,2,3],bar:[4,5,6]}");
        t2 = Tensor.from("tensor(a{},x[2]):{foo:[3,5],bar:[9,11]}");
        checkResult(t2, map3to2(t1, "double"), TensorType.Value.DOUBLE);
        checkResult(t2, map3to2(t1, "float"), TensorType.Value.FLOAT);
        checkResult(t2, map3to2(t1, "bfloat16"), TensorType.Value.BFLOAT16);
        checkResult(t2, map3to2(t1, "int8"), TensorType.Value.INT8);
        t1 = Tensor.from("tensor(x[3]):[3,4,6]");
        t2 = Tensor.from("tensor(x[2]):[7,10]");
        checkResult(t2, map3to2(t1, "double"), TensorType.Value.DOUBLE);
        checkResult(t2, map3to2(t1, "float"), TensorType.Value.FLOAT);
        checkResult(t2, map3to2(t1, "bfloat16"), TensorType.Value.BFLOAT16);
        checkResult(t2, map3to2(t1, "int8"), TensorType.Value.INT8);
        t1 = Tensor.from("tensor(x[4],z{}):{foo:[1,2,3,99],bar:[4,5,6,99]}");
        t2 = Tensor.from("tensor(x[2],z{}):{foo:[3,5],bar:[9,11]}");
        checkResult(t2, map3to2(t1, "double"), TensorType.Value.DOUBLE);
        checkResult(t2, map3to2(t1, "float"), TensorType.Value.FLOAT);
        checkResult(t2, map3to2(t1, "bfloat16"), TensorType.Value.BFLOAT16);
        checkResult(t2, map3to2(t1, "int8"), TensorType.Value.INT8);
        t1 = Tensor.from("tensor(a{},x[3],z{}):{" +
                         "{a:aa,x:0,z:kz}:1," +
                         "{a:aa,x:1,z:kz}:2," +
                         "{a:aa,x:2,z:kz}:3," +
                         "{a:ba,x:0,z:kz}:4," +
                         "{a:ba,x:1,z:kz}:5," +
                         "{a:ba,x:2,z:kz}:6," +
                         "{a:ba,x:0,z:nz}:7," +
                         "{a:ba,x:1,z:nz}:8," +
                         "{a:ba,x:2,z:nz}:9" + "}");
        t2 = Tensor.from("tensor(a{},x[2],z{}):{" +
                         "{a:aa,x:0,z:kz}:3," +
                         "{a:aa,x:1,z:kz}:5," +
                         "{a:ba,x:0,z:kz}:9," +
                         "{a:ba,x:1,z:kz}:11," +
                         "{a:ba,x:0,z:nz}:15," +
                         "{a:ba,x:1,z:nz}:17" + "}");
        checkResult(t2, map3to2(t1, "double"), TensorType.Value.DOUBLE);
        checkResult(t2, map3to2(t1, "float"), TensorType.Value.FLOAT);
        checkResult(t2, map3to2(t1, "bfloat16"), TensorType.Value.BFLOAT16);
        checkResult(t2, map3to2(t1, "int8"), TensorType.Value.INT8);
    }

    static class MyCellGenFromScalar implements ScalarFunction<Name> {
        @Override public Double apply(EvaluationContext<Name> context) {
            double input = context.getTensor("denseInput").asDouble();
            double dimIdx = context.getTensor("x").asDouble();
            double value = input + dimIdx * 2;
            return value;
        }
    }

    private static Tensor map1to3(Tensor input, String cellType) {
        TensorType tt = TensorType.fromSpec("tensor<" + cellType + ">(x[3])");
        var tfun = Generate.<Name>bound(tt, new MyCellGenFromScalar());
        var constInput = new ConstantTensor<Name>(input);
        var mapper = new MapSubspaces<Name>(constInput, "denseInput", tfun);
        Tensor mapped = mapper.evaluate();
        System.err.println("Mapped 1->3: " + mapped);
        return mapped;
    }

    @Test
    public void testFromSparse() {
        Tensor t1, t2;
        t1 = Tensor.from("tensor(a{}):{foo:2,bar:17}");
        t2 = Tensor.from("tensor(a{},x[3]):{foo:[2,4,6],bar:[17,19,21]}");
        checkResult(t2, map1to3(t1, "double"), TensorType.Value.DOUBLE);
        checkResult(t2, map1to3(t1, "float"), TensorType.Value.FLOAT);
        checkResult(t2, map1to3(t1, "bfloat16"), TensorType.Value.BFLOAT16);
        checkResult(t2, map1to3(t1, "int8"), TensorType.Value.INT8);
        t1 = Tensor.from("tensor():{5}");
        t2 = Tensor.from("tensor(x[3]):[5,7,9]");
        checkResult(t2, map1to3(t1, "double"), TensorType.Value.DOUBLE);
        checkResult(t2, map1to3(t1, "float"), TensorType.Value.FLOAT);
        checkResult(t2, map1to3(t1, "bfloat16"), TensorType.Value.BFLOAT16);
        checkResult(t2, map1to3(t1, "int8"), TensorType.Value.INT8);
        t1 = Tensor.from("tensor<float>(a{}):{foo:2,bar:17}");
        t2 = Tensor.from("tensor(a{},x[3]):{foo:[2,4,6],bar:[17,19,21]}");
        checkResult(t2, map1to3(t1, "double"), TensorType.Value.DOUBLE);
        checkResult(t2, map1to3(t1, "float"), TensorType.Value.FLOAT);
        checkResult(t2, map1to3(t1, "bfloat16"), TensorType.Value.BFLOAT16);
        checkResult(t2, map1to3(t1, "int8"), TensorType.Value.INT8);
    }

    static class MyWeightedSum extends TensorFunction<Name> {
        public List<TensorFunction<Name>> arguments() { return List.of(); }
        public TensorFunction<Name> withArguments(List<TensorFunction<Name>> arguments) { return this; }
        public PrimitiveTensorFunction<Name> toPrimitive() { return null; }
        public Tensor evaluate(EvaluationContext<Name> context) {
            Tensor input = context.getTensor("denseInput");
            double value = 0.0;
            double w = 8.0;
            long sz = input.type().dimensions().get(0).size().get();
            for (long i = 0; i < sz; i++) {
                var addr = TensorAddress.of(i);
                value += w * input.get(addr);
                w = w * 0.5;
            }
            return Tensor.from(value);
        }
        public TensorType type(TypeContext<Name> context) { return TensorType.empty; }
        public String toString(ToStringContext<Name> context) { return "MyWeightedSum(denseInput)"; }
        public int hashCode() { return 0; }
    }

    private static Tensor mapNto1(Tensor input) {
        var tfun = new MyWeightedSum();
        var constInput = new ConstantTensor<Name>(input);
        var mapper = new MapSubspaces<Name>(constInput, "denseInput", tfun);
        Tensor mapped = mapper.evaluate();
        System.err.println("Mapped N->1: " + mapped);
        return mapped;
    }

    @Test
    public void testToSparse() {
        Tensor t1, t2;
        t1 = Tensor.from("tensor(a{},x[3]):{foo:[2,4,6],bar:[17,19,21]}");
        t2 = Tensor.from("tensor(a{}):{foo:44,bar:254}");
        checkResult(t2, mapNto1(t1), TensorType.Value.DOUBLE);
        checkResult(t2, mapNto1(t1.cellCast(TensorType.Value.FLOAT)), TensorType.Value.FLOAT);
        checkResult(t2, mapNto1(t1.cellCast(TensorType.Value.BFLOAT16)), TensorType.Value.FLOAT);
        checkResult(t2, mapNto1(t1.cellCast(TensorType.Value.INT8)), TensorType.Value.FLOAT);
        t1 = Tensor.from("tensor(a{},x[4]):{foo:[2,4,6,8],bar:[1,1,1,1]}");
        t2 = Tensor.from("tensor(a{}):{foo:52,bar:15}");
        checkResult(t2, mapNto1(t1), TensorType.Value.DOUBLE);
        checkResult(t2, mapNto1(t1.cellCast(TensorType.Value.FLOAT)), TensorType.Value.FLOAT);
        checkResult(t2, mapNto1(t1.cellCast(TensorType.Value.BFLOAT16)), TensorType.Value.FLOAT);
        checkResult(t2, mapNto1(t1.cellCast(TensorType.Value.INT8)), TensorType.Value.FLOAT);
    }

    private static Tensor mapIdentity(Tensor input) {
        var tfun = new VariableTensor<Name>("denseInput");
        var constInput = new ConstantTensor<Name>(input);
        var mapper = new MapSubspaces<Name>(constInput, "denseInput", tfun);
        Tensor mapped = mapper.evaluate();
        System.err.println("Identity mapped: " + mapped);
        return mapped;
    }

    @Test
    public void testIdentityMapping() {
        Tensor t1;
        t1 = Tensor.from("tensor(a{},x[3]):{foo:[2,4,6],bar:[17,19,21]}");
        checkResult(t1, mapIdentity(t1), TensorType.Value.DOUBLE);
        checkResult(t1, mapIdentity(t1.cellCast(TensorType.Value.FLOAT)), TensorType.Value.FLOAT);
        checkResult(t1, mapIdentity(t1.cellCast(TensorType.Value.BFLOAT16)), TensorType.Value.BFLOAT16);
        checkResult(t1, mapIdentity(t1.cellCast(TensorType.Value.INT8)), TensorType.Value.INT8);
        t1 = Tensor.from("tensor(a{}):{foo:17,bar:42}");
        checkResult(t1, mapIdentity(t1), TensorType.Value.DOUBLE);
        checkResult(t1, mapIdentity(t1.cellCast(TensorType.Value.FLOAT)), TensorType.Value.FLOAT);
        checkResult(t1, mapIdentity(t1.cellCast(TensorType.Value.BFLOAT16)), TensorType.Value.FLOAT);
        checkResult(t1, mapIdentity(t1.cellCast(TensorType.Value.INT8)), TensorType.Value.FLOAT);
        t1 = Tensor.from("tensor(y[4]):[2,3,4,5]");
        checkResult(t1, mapIdentity(t1), TensorType.Value.DOUBLE);
        checkResult(t1, mapIdentity(t1.cellCast(TensorType.Value.FLOAT)), TensorType.Value.FLOAT);
        checkResult(t1, mapIdentity(t1.cellCast(TensorType.Value.BFLOAT16)), TensorType.Value.BFLOAT16);
        checkResult(t1, mapIdentity(t1.cellCast(TensorType.Value.INT8)), TensorType.Value.INT8);
        t1 = Tensor.from(42);
        assertEquals(t1, mapIdentity(t1));
    }

}
