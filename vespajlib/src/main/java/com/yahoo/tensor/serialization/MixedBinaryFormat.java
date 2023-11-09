// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.tensor.serialization;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.MixedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Implementation of a mixed binary format for a tensor.
 * See eval/src/vespa/eval/tensor/serialization/format.txt for format.
 *
 * @author lesters
 */
class MixedBinaryFormat implements BinaryFormat {

    private final TensorType.Value serializationValueType;

    MixedBinaryFormat() {
        this(TensorType.Value.DOUBLE);
    }
    MixedBinaryFormat(TensorType.Value serializationValueType) {
        this.serializationValueType = serializationValueType;
    }

    @Override
    public void encode(GrowableByteBuffer buffer, Tensor tensor) {
        if ( ! ( tensor instanceof MixedTensor))
            throw new RuntimeException("The mixed format is only supported for mixed tensors");
        MixedTensor mixed = (MixedTensor) tensor;
        encodeSparseDimensions(buffer, mixed);
        encodeDenseDimensions(buffer, mixed);
        encodeCells(buffer, mixed);
    }

    private void encodeSparseDimensions(GrowableByteBuffer buffer, MixedTensor tensor) {
        List<TensorType.Dimension> sparseDimensions = tensor.type().dimensions().stream().filter(d -> !d.isIndexed()).toList();
        buffer.putInt1_4Bytes(sparseDimensions.size());
        for (TensorType.Dimension dimension : sparseDimensions) {
            buffer.putUtf8String(dimension.name());
        }
    }

    private void encodeDenseDimensions(GrowableByteBuffer buffer, MixedTensor tensor) {
        List<TensorType.Dimension> denseDimensions = tensor.type().dimensions().stream().filter(d -> d.isIndexed()).toList();
        buffer.putInt1_4Bytes(denseDimensions.size());
        for (TensorType.Dimension dimension : denseDimensions) {
            buffer.putUtf8String(dimension.name());
            buffer.putInt1_4Bytes((int)dimension.size().orElseThrow(() ->
                                  new IllegalArgumentException("Unknown size of indexed dimension.")).longValue());  // XXX: Size truncation
        }
    }

    private void encodeCells(GrowableByteBuffer buffer, MixedTensor tensor) {
        switch (serializationValueType) {
            case DOUBLE: encodeCells(buffer, tensor, buffer::putDouble); break;
            case FLOAT: encodeCells(buffer, tensor, (val) -> buffer.putFloat(val.floatValue())); break;
            case BFLOAT16: encodeCells(buffer, tensor, (val) ->
                    buffer.putShort(TypedBinaryFormat.bFloat16BitsFromFloat(val.floatValue()))); break;
            case INT8: encodeCells(buffer, tensor, (val) -> buffer.put(((byte)val.floatValue()))); break;
        }
    }

    private void encodeCells(GrowableByteBuffer buffer, MixedTensor tensor, Consumer<Double> consumer) {
        List<TensorType.Dimension> sparseDimensions = tensor.type().dimensions().stream().filter(d -> !d.isIndexed()).toList();
        long denseSubspaceSize = tensor.denseSubspaceSize();
        var denseSubspaces = tensor.getInternalDenseSubspaces();
        if (sparseDimensions.size() > 0) {
            buffer.putInt1_4Bytes(denseSubspaces.size());
        }
        for (var subspace : denseSubspaces) {
            for (int index = 0; index < subspace.sparseAddress.size(); index++) {
                buffer.putUtf8String(subspace.sparseAddress.label(index));
            }
            for (double val : subspace.cells) {
                consumer.accept(val);
            }
        }
    }

    @Override
    public Tensor decode(Optional<TensorType> optionalType, GrowableByteBuffer buffer) {
        TensorType type;
        if (optionalType.isPresent()) {
            type = optionalType.get();
            if (type.valueType() != this.serializationValueType) {
                throw new IllegalArgumentException("Tensor value type mismatch. Value type " + type.valueType() +
                        " is not " + this.serializationValueType);
            }
            TensorType serializedType = decodeType(buffer);
            if ( ! serializedType.isAssignableTo(type))
                throw new IllegalArgumentException("Type/instance mismatch: A tensor of type " + serializedType +
                                                   " cannot be assigned to type " + type);
        }
        else {
            type = decodeType(buffer);
        }
        MixedTensor.BoundBuilder builder = (MixedTensor.BoundBuilder)MixedTensor.Builder.of(type);
        decodeCells(buffer, builder, type);
        return builder.build();
    }

    private TensorType decodeType(GrowableByteBuffer buffer) {
        TensorType.Builder builder = new TensorType.Builder(serializationValueType);
        int numMappedDimensions = buffer.getInt1_4Bytes();
        for (int i = 0; i < numMappedDimensions; ++i) {
            builder.mapped(buffer.getUtf8String());
        }
        int numIndexedDimensions = buffer.getInt1_4Bytes();
        for (int i = 0; i < numIndexedDimensions; ++i) {
            builder.indexed(buffer.getUtf8String(), buffer.getInt1_4Bytes());  // XXX: Size truncation
        }
        return builder.build();
    }

    private void decodeCells(GrowableByteBuffer buffer, MixedTensor.BoundBuilder builder, TensorType type) {
        switch (serializationValueType) {
            case DOUBLE: decodeCells(buffer, builder, type, buffer::getDouble); break;
            case FLOAT: decodeCells(buffer, builder, type, () -> (double)buffer.getFloat()); break;
            case BFLOAT16: decodeCells(buffer, builder, type, () ->
                    (double)TypedBinaryFormat.floatFromBFloat16Bits(buffer.getShort())); break;
            case INT8: decodeCells(buffer, builder, type, () -> (double)buffer.get()); break;
        }
    }

    private void decodeCells(GrowableByteBuffer buffer, MixedTensor.BoundBuilder builder, TensorType type, Supplier<Double> supplier) {
        List<TensorType.Dimension> sparseDimensions = type.dimensions().stream().filter(d -> !d.isIndexed()).toList();
        TensorType sparseType = MixedTensor.createPartialType(type.valueType(), sparseDimensions);
        long denseSubspaceSize = builder.denseSubspaceSize();

        int numBlocks = 1;
        if (sparseDimensions.size() > 0) {
            numBlocks = buffer.getInt1_4Bytes();
        }

        double[] denseSubspace = new double[(int)denseSubspaceSize];
        for (int i = 0; i < numBlocks; ++i) {
            TensorAddress.Builder sparseAddress = new TensorAddress.Builder(sparseType);
            for (TensorType.Dimension sparseDimension : sparseDimensions) {
                sparseAddress.add(sparseDimension.name(), buffer.getUtf8String());
            }
            for (long denseOffset = 0; denseOffset < denseSubspaceSize; denseOffset++) {
                denseSubspace[(int)denseOffset] = supplier.get();
            }
            builder.block(sparseAddress.build(), denseSubspace);
        }
    }

}
