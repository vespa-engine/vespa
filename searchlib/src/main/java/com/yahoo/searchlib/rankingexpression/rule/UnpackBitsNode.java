// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.api.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

/**
 * Macro that expands to the appropriate map_subspaces magic incantation
 *
 * @author arnej
 */
@Beta
public class UnpackBitsNode extends CompositeNode {

    private static String operationName = "unpack_bits";
    private enum EndianNess {
        BIG_ENDIAN("big"), LITTLE_ENDIAN("little");

        private final String id;
        EndianNess(String id) { this.id = id; }
        public String toString() { return id; }
        public static EndianNess fromId(String id) {
            for (EndianNess value : values()) {
                if (value.id.equals(id)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("EndianNess must be either 'big' or 'little', but was '" + id + "'");
        }
    };

    final ExpressionNode input;
    final TensorType.Value targetCellType;
    final EndianNess endian;

    public UnpackBitsNode(ExpressionNode input, TensorType.Value targetCellType, String endianNess) {
        this.input = input;
        this.targetCellType = targetCellType;
        this.endian = EndianNess.fromId(endianNess);
    }

    @Override
    public List<ExpressionNode> children() {
        return Collections.singletonList(input);
    }

    private static record Meta(TensorType outputType, TensorType outputDenseType, String unpackDimension) {}

    @Override
    public StringBuilder toString(StringBuilder string, SerializationContext context, Deque<String> path, CompositeNode parent) {
        var optTC = context.typeContext();
        if (optTC.isPresent()) {
            TensorType inputType = input.type(optTC.get());
            var meta = analyze(inputType);
            string.append("map_subspaces").append("(");
            input.toString(string, context, path, this);
            string.append(", f(denseSubspaceInput)(");
            string.append(meta.outputDenseType()).append("("); // generate
            string.append("bit(denseSubspaceInput{");
            for (var dim : meta.outputDenseType().dimensions()) {
                String dName = dim.name();
                boolean last = dName.equals(meta.unpackDimension);
                string.append(dName);
                string.append(":(");
                string.append(dName);
                if (last) {
                    string.append("/8");
                }
                string.append(")");
                if (! last) {
                    string.append(", ");
                }
            }
            if (endian.equals(EndianNess.BIG_ENDIAN)) {
                string.append("}, 7-(");
            } else {
                string.append("}, (");
            }
            string.append(meta.unpackDimension);
            string.append(" % 8)");
            string.append("))))"); // bit, generate, f, map_subspaces
        } else {
            string.append(operationName);
            string.append("(");
            input.toString(string, context, path, this);
            string.append(",");
            string.append(targetCellType);
            string.append(",");
            string.append(endian);
            string.append(")");
        }
        return string;
    }

    @Override
    public Value evaluate(Context context) {
        Tensor inputTensor = input.evaluate(context).asTensor();
        TensorType inputType = inputTensor.type();
        var meta = analyze(inputType);
        var builder = Tensor.Builder.of(meta.outputType());
        for (var iter = inputTensor.cellIterator(); iter.hasNext(); ) {
            var cell = iter.next();
            var oldAddr = cell.getKey();
            for (int bitIdx = 0; bitIdx < 8; bitIdx++) {
                var addrBuilder = new TensorAddress.Builder(meta.outputType());
                for (int i = 0; i < inputType.dimensions().size(); i++) {
                    var dim = inputType.dimensions().get(i);
                    if (dim.name().equals(meta.unpackDimension())) {
                        long newIdx = oldAddr.numericLabel(i) * 8 + bitIdx;
                        addrBuilder.add(dim.name(), String.valueOf(newIdx));
                    } else {
                        addrBuilder.add(dim.name(), oldAddr.label(i));
                    }
                }
                var newAddr = addrBuilder.build();
                int oldValue = (int)(cell.getValue().doubleValue());
                if (endian.equals(EndianNess.BIG_ENDIAN)) {
                    float newCellValue = 1 & (oldValue >>> (7 - bitIdx));
                    builder.cell(newAddr, newCellValue);
                } else {
                    float newCellValue = 1 & (oldValue >>> bitIdx);
                    builder.cell(newAddr, newCellValue);
                }
            }
        }
        return new TensorValue(builder.build());
    }

    private Meta analyze(TensorType inputType) {
        if (inputType.valueType() != TensorType.Value.INT8) {
            throw new IllegalArgumentException("bad " + operationName + "; input must have cell-type int8, but it was: " + inputType.valueType());
        }
        TensorType inputDenseType = inputType.indexedSubtype();
        if (inputDenseType.rank() == 0) {
            throw new IllegalArgumentException("bad " + operationName + "; input must have indexed dimension, but type was: " + inputType);
        }
        var lastDim = inputDenseType.dimensions().get(inputDenseType.rank() - 1);
        if (lastDim.size().isEmpty()) {
            throw new IllegalArgumentException("bad " + operationName + "; last indexed dimension must be bound, but type was: " + inputType);
        }
        List<TensorType.Dimension> outputDims = new ArrayList<>();
        var ttBuilder = new TensorType.Builder(targetCellType);
        for (var dim : inputType.dimensions()) {
            if (dim.name().equals(lastDim.name())) {
                long sz = dim.size().get();
                ttBuilder.indexed(dim.name(), sz * 8);
            } else {
                ttBuilder.set(dim);
            }
        }
        TensorType outputType = ttBuilder.build();
        return new Meta(outputType, outputType.indexedSubtype(), lastDim.name());
    }

    @Override
    public TensorType type(TypeContext<Reference> context) {
        TensorType inputType = input.type(context);
        var meta = analyze(inputType);
        return meta.outputType();
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> newChildren) {
        if (newChildren.size() != 1)
            throw new IllegalArgumentException("Expected 1 child but got " + newChildren.size());
        return new UnpackBitsNode(newChildren.get(0), targetCellType, endian.toString());
    }

    @Override
    public int hashCode() { return Objects.hash(operationName, input, targetCellType); }

}
