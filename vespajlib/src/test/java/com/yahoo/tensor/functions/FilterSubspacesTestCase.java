// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.evaluation.VariableTensor;

import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author arnej
 */
public class FilterSubspacesTestCase {

    static class Identity extends TensorFunction<Name> {
        @Override public Tensor evaluate(EvaluationContext<Name> context) {
            Tensor input = context.getTensor("denseInput");
            return input;
        }
        public int hashCode() { return 0; }
        public String toString(ToStringContext<Name> ctx) { return "identity"; }
        public TensorType type(TypeContext<Name> ctx) { return ctx.getType("denseInput"); }
        public PrimitiveTensorFunction<Name> toPrimitive() { return null; }
        public TensorFunction<Name> withArguments(List<TensorFunction<Name>> arguments) { return null; }
        public List<TensorFunction<Name>> arguments() { return null; }
    }


    static class SumFilter extends TensorFunction<Name> {
        final Function<Double, Boolean> filter;
        SumFilter(Function<Double, Boolean> filter) { this.filter = filter; }
        @Override public Tensor evaluate(EvaluationContext<Name> context) {
            Tensor input = context.getTensor("denseInput");
            double value = input.sum().asDouble();
            return Tensor.from(filter.apply(value) ? 1.0 : 0.0);
        }
        public int hashCode() { return 42; }
        public String toString(ToStringContext<Name> ctx) { return "SumFilter"; }
        public TensorType type(TypeContext<Name> ctx) { return TensorType.empty; }
        public PrimitiveTensorFunction<Name> toPrimitive() {
            var input = new VariableTensor<Name>("denseInput");
            var reduce = new Reduce<Name>(input, Reduce.Aggregator.sum);
            var apply = new Map<Name>(reduce, value -> (filter.apply(value) ? 1.0 : 0.0));
            return apply;
        }
        public TensorFunction<Name> withArguments(List<TensorFunction<Name>> arguments) { return null; }
        public List<TensorFunction<Name>> arguments() { return null; }
    }

    private static Tensor filterGreater(Tensor input, double threshold) {
        var tfun = new SumFilter(sum -> (sum > threshold));
        var constInput = new ConstantTensor<Name>(input);
        var mapper = new FilterSubspaces<Name>(constInput, "denseInput", tfun);
        Tensor mapped = mapper.evaluate();
        System.err.println("Filter > " + threshold + " : " + mapped);

        var primFun = tfun.toPrimitive();
        var prim = new FilterSubspaces<Name>(constInput, "denseInput", primFun);
        // System.err.println("Primitives: " + prim);
        assertEquals(mapped, prim.evaluate());
        return mapped;
    }

    private static Tensor filterOdd(Tensor input) {
        var tfun = new SumFilter(sum -> ((sum % 2) == 1));
        var constInput = new ConstantTensor<Name>(input);
        var mapper = new FilterSubspaces<Name>(constInput, "denseInput", tfun);
        Tensor mapped = mapper.evaluate();
        System.err.println("Filter odd : " + mapped);
        return mapped;
    }

    private static Tensor filterEven(Tensor input) {
        var tfun = new SumFilter(sum -> ((sum % 2) == 0));
        var constInput = new ConstantTensor<Name>(input);
        var mapper = new FilterSubspaces<Name>(constInput, "denseInput", tfun);
        Tensor mapped = mapper.evaluate();
        System.err.println("Filter even : " + mapped);
        return mapped;
    }

    private static void checkGt(Tensor input, double threshold, Tensor expect) {
        var result = filterGreater(input, threshold);
        assertEquals(expect, result);
        var in8 = input.cellCast(TensorType.Value.INT8);
        var ex8 = expect.cellCast(TensorType.Value.INT8);
        result = filterGreater(in8, threshold);
        assertEquals(ex8, result);

        var in16 = input.cellCast(TensorType.Value.BFLOAT16);
        var ex16 = expect.cellCast(TensorType.Value.BFLOAT16);
        result = filterGreater(in16, threshold);
        assertEquals(ex16, result);

        var in32 = input.cellCast(TensorType.Value.FLOAT);
        var ex32 = expect.cellCast(TensorType.Value.FLOAT);
        result = filterGreater(in32, threshold);
        assertEquals(ex32, result);
    }

    @Test
    public void testBasicFilter() {
        Tensor in, out;
        in = Tensor.from("tensor(p{}):{a:1,b:2,c:3,d:4,e:5,f:6,g:7}");
        checkGt(in, 0, in);
        out = Tensor.from("tensor(p{}):{d:4,e:5,f:6,g:7}");
        checkGt(in, 3, out);
        out = Tensor.from("tensor(p{}):{}");
        checkGt(in, 7, out);
        out = Tensor.from("tensor(p{}):{a:1,c:3,e:5,g:7}");
        assertEquals(out, filterOdd(in));
        out = Tensor.from("tensor(p{}):{b:2,d:4,f:6}");
        assertEquals(out, filterEven(in));
    }

    @Test
    public void testFilterMixed() {
        Tensor in, out;
        in = Tensor.from("tensor(p{},x[2]):{a:[1,2],b:[2,4],c:[0,3],d:[4,0],e:[1,1],f:[0,0],g:[3,4]}");
        checkGt(in, -1, in);
        out = Tensor.from("tensor(p{},x[2]):{a:[1,2],b:[2,4],c:[0,3],d:[4,0],e:[1,1],g:[3,4]}");
        checkGt(in, 0, out);
        out = Tensor.from("tensor(p{},x[2]):{b:[2,4],d:[4,0],g:[3,4]}");
        checkGt(in, 3, out);
        out = Tensor.from("tensor(p{},x[2]):{a:[1,2],c:[0,3],g:[3,4]}");
        assertEquals(out, filterOdd(in));
        out = Tensor.from("tensor(p{},x[2]):{b:[2,4],d:[4,0],e:[1,1],f:[0,0]}");
        assertEquals(out, filterEven(in));
    }

    private static Tensor filterIdentity(Tensor input) {
        var tfun = new Identity();
        var constInput = new ConstantTensor<Name>(input);
        var mapper = new FilterSubspaces<Name>(constInput, "denseInput", tfun);
        Tensor mapped = mapper.evaluate();
        System.err.println("Filter nonzero sum: " + mapped);
        return mapped;
    }

    @Test
    public void testIdentityFilter() {
        Tensor in, out;
        in = Tensor.from("tensor(p{}):{a:1,b:0,c:3,d:4,e:0,f:0,g:7}");
        out = Tensor.from("tensor(p{}):{a:1,c:3,d:4,g:7}");
        assertEquals(out, filterIdentity(in));
        in = Tensor.from("tensor(p{},x[2]):{a:[1,2],b:[0,0],c:[0,-1],d:[1,-1]}");
        out = Tensor.from("tensor(p{},x[2]):{a:[1,2],c:[0,-1]}");
        assertEquals(out, filterIdentity(in));
        in = Tensor.from("tensor(p{},x[2],y[3]):{a:[[1,2,3],[4,6,6]],b:[[0,0,0],[0,0,0]]}");
        out = Tensor.from("tensor(p{},x[2],y[3]):{a:[[1,2,3],[4,6,6]]}");
        assertEquals(out, filterIdentity(in));
    }

}
