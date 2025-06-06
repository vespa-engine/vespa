// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.MapEvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

/**
 * A function suitable for use in MapSubspaces / FilterSubspaces
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

    DenseSubspaceFunction<NAMETYPE> toPrimitive() {
        return new DenseSubspaceFunction<>(argName, function.toPrimitive());
    }

    class MyTypeContext implements TypeContext<NAMETYPE> {
        private final TensorType subspaceType;

        MyTypeContext(TensorType subspaceType) {
            this.subspaceType = subspaceType;
        }

        public TensorType getType(NAMETYPE name) {
            return getType(name.name());
        }

        public TensorType getType(String name) {
            return argName.equals(name) ? subspaceType : null;
        }

        public String resolveBinding(String name) {
            return name;
        }
    }

    TensorType outputType(TensorType subspaceType) {
        var context = new MyTypeContext(subspaceType);
        var result = function.type(context);
        return result;
    }

    public String toString() {
        return "f(" + argName + ")(" + function + ")";
    }
}
