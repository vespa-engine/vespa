// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.functions;

import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * The <i>cell_cast</i> tensor function creates a new tensor with the specified cell value type.
 *
 * @author lesters
 */
public class CellCast<NAMETYPE extends Name> extends PrimitiveTensorFunction<NAMETYPE> {

    private final TensorFunction<NAMETYPE> argument;
    private final TensorType.Value valueType;

    public CellCast(TensorFunction<NAMETYPE> argument, TensorType.Value valueType) {
        Objects.requireNonNull(argument, "The argument tensor cannot be null");
        Objects.requireNonNull(valueType, "The value type cannot be null");
        this.argument = argument;
        this.valueType = valueType;
    }

    @Override
    public List<TensorFunction<NAMETYPE>> arguments() { return Collections.singletonList(argument); }

    @Override
    public TensorFunction<NAMETYPE> withArguments(List<TensorFunction<NAMETYPE>> arguments) {
        if ( arguments.size() != 1)
            throw new IllegalArgumentException("CellCast must have 1 argument, got " + arguments.size());
        return new CellCast<>(arguments.get(0), valueType);
    }

    @Override
    public PrimitiveTensorFunction<NAMETYPE> toPrimitive() {
        return new CellCast<>(argument.toPrimitive(), valueType);
    }

    @Override
    public TensorType type(TypeContext<NAMETYPE> context) {
        return new TensorType(valueType, argument.type(context).dimensions());
    }

    @Override
    public Tensor evaluate(EvaluationContext<NAMETYPE> context) {
        Tensor tensor = argument.evaluate(context);
        if (tensor.type().valueType() == valueType) {
            return tensor;
        }
        TensorType type = new TensorType(valueType, tensor.type().dimensions());
        return cast(tensor, type);
    }

    private Tensor cast(Tensor tensor, TensorType type) {
        Tensor.Builder builder = Tensor.Builder.of(type);
        TensorType.Value fromValueType = tensor.type().valueType();
        for (Iterator<Tensor.Cell> i = tensor.cellIterator(); i.hasNext(); ) {
            Tensor.Cell cell = i.next();
            if (fromValueType == TensorType.Value.FLOAT) {
                builder.cell(cell.getKey(), cell.getFloatValue());
            } else if (fromValueType == TensorType.Value.DOUBLE) {
                builder.cell(cell.getKey(), cell.getDoubleValue());
            } else {
                builder.cell(cell.getKey(), cell.getValue());
            }
        }
        return builder.build();
    }

    @Override
    public String toString(ToStringContext context) {
        return "cell_cast(" + argument.toString(context) + ", " + valueType + ")";
    }

}
