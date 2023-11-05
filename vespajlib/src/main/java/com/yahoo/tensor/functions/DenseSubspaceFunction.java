// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.MapEvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Optional;
import java.util.function.Function;

/**
 * A function suitable for use in MapSubspaces
 *
 * @author arnej
 */
class DenseSubspaceFunction<NAMETYPE extends Name> {

    private final String argName;
    private final TensorFunction<NAMETYPE> function;

    public DenseSubspaceFunction(String argName, TensorFunction<NAMETYPE> function) {
        this.argName = argName;
        this.function = function;
    }

    Tensor map(Tensor subspace) {
        var context = new MapEvaluationContext<NAMETYPE>();
        context.put(argName, subspace);
        return function.evaluate(context);
    }

    class MyTypeContext implements TypeContext<NAMETYPE> {
        private final TensorType subspaceType;
        MyTypeContext(TensorType subspaceType) { this.subspaceType = subspaceType; }
        public TensorType getType(NAMETYPE name) { return getType(name.name()); }
        public TensorType getType(String name) { return argName.equals(name) ? subspaceType : null; }
    }

    TensorType outputType(TensorType subspaceType) {
        var context = new MyTypeContext(subspaceType);
        var result = function.type(context);
        if (result.mappedSubtype().rank() > 0) {
            throw new IllegalArgumentException("function used in map_subspaces type had mapped dimensions: " + result);
        }
        return result;
    }

    public String toString() {
        return "f(" + argName + ")(" + function + ")";
    }

}
