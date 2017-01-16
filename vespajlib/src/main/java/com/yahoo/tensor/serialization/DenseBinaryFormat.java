package com.yahoo.tensor.serialization;

import com.google.common.annotations.Beta;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;
import java.util.Optional;

/**
 * Implementation of a dense binary format for a tensor on the form:
 *
 * Sorted dimensions = num_dimensions [dimension_str_len dimension_str_bytes dimension_size_int]*
 * Cell_values = [double, double, double, ...]*
 * where values are encoded in order of increasing indexes in each dimension, increasing 
 * indexes of later dimensions in the dimension type before earlier.
 * 
 * @author bratseth
 */
@Beta
public class DenseBinaryFormat implements BinaryFormat {

    @Override
    public void encode(GrowableByteBuffer buffer, Tensor tensor) {
        if ( ! ( tensor instanceof IndexedTensor))
            throw new RuntimeException("The dense format is only supported for indexed tensors");
        encodeDimensions(buffer, (IndexedTensor)tensor);
        encodeCells(buffer, tensor);
    }

    private void encodeDimensions(GrowableByteBuffer buffer, IndexedTensor tensor) {
        buffer.putInt1_4Bytes(tensor.type().dimensions().size());
        for (int i = 0; i < tensor.type().dimensions().size(); i++) {
            buffer.putUtf8String(tensor.type().dimensions().get(i).name());
            buffer.putInt1_4Bytes(tensor.dimensionSizes().size(i));
        }
    }

    private void encodeCells(GrowableByteBuffer buffer, Tensor tensor) {
        Iterator<Double> i = tensor.valueIterator();
        while (i.hasNext())
            buffer.putDouble(i.next());
    }

    @Override
    public Tensor decode(Optional<TensorType> optionalType, GrowableByteBuffer buffer) {
        TensorType type;
        DimensionSizes sizes;
        if (optionalType.isPresent()) {
            type = optionalType.get();
            sizes = decodeAndValidateDimensionSizes(type, buffer);
        }
        else {
            type = decodeType(buffer);
            sizes = sizesFromType(type);
        }
        Tensor.Builder builder = Tensor.Builder.of(type, sizes);
        decodeCells(sizes, buffer, (IndexedTensor.BoundBuilder)builder);
        return builder.build();
    }

    private DimensionSizes decodeAndValidateDimensionSizes(TensorType type, GrowableByteBuffer buffer) {
        int dimensionCount = buffer.getInt1_4Bytes();
        if (type.dimensions().size() != dimensionCount)
            throw new IllegalArgumentException("Type/instance mismatch: Instance has " + dimensionCount + 
                                               " dimensions but type is " + type);
        
        DimensionSizes.Builder builder = new DimensionSizes.Builder(dimensionCount);
        for (int i = 0; i < dimensionCount; i++) {
            TensorType.Dimension expectedDimension = type.dimensions().get(i);

            String encodedName = buffer.getUtf8String();
            int encodedSize = buffer.getInt1_4Bytes();

            if ( ! expectedDimension.name().equals(encodedName))
                throw new IllegalArgumentException("Type/instance mismatch: Instance has '" + encodedName +
                                                   "' as dimension " + i + " but type is " + type);

            if (expectedDimension.size().isPresent() && expectedDimension.size().get() < encodedSize)
                throw new IllegalArgumentException("Type/instance mismatch: Instance has size " + encodedSize + 
                                                   " in " + expectedDimension  + " in type " + type);

            builder.set(i, encodedSize);
        }
        return builder.build();
    }

    private TensorType decodeType(GrowableByteBuffer buffer) {
        int dimensionCount = buffer.getInt1_4Bytes();
        TensorType.Builder builder = new TensorType.Builder();
        for (int i = 0; i < dimensionCount; i++)
            builder.indexed(buffer.getUtf8String(), buffer.getInt1_4Bytes());
        return builder.build();
    }

    /** Returns dimension sizes from a type consisting of fully specified, indexed dimensions only */
    private DimensionSizes sizesFromType(TensorType type) {
        DimensionSizes.Builder builder = new DimensionSizes.Builder(type.dimensions().size());
        for (int i = 0; i < type.dimensions().size(); i++)
            builder.set(i, type.dimensions().get(i).size().get());
        return builder.build();
    }

    private void decodeCells(DimensionSizes sizes, GrowableByteBuffer buffer, IndexedTensor.BoundBuilder builder) {
        for (int i = 0; i < sizes.totalSize(); i++)
            builder.cellByDirectIndex(i, buffer.getDouble());
    }

}
