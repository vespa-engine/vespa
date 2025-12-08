// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.yahoo.data.disclosure.DataSink;
import com.yahoo.data.disclosure.DataSource;
import com.yahoo.tensor.serialization.JsonFormat;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * Emits tensor data to a DataSink, allowing format-agnostic tensor rendering.
 * This eliminates the need for intermediate JSON string creation.
 *
 * @author andreer
 * @author arnej
 */
public class TensorDataSource implements DataSource {

    private final Tensor tensor;
    private final JsonFormat.EncodeOptions options;
    private final boolean wrapAndType;
    private boolean inObject = false;

    public TensorDataSource(Tensor tensor, JsonFormat.EncodeOptions options) {
        this.tensor = tensor;
        this.options = options;
        this.wrapAndType = !options.directValues();
    }

    @Override
    public void emit(DataSink sink) {
        wrapStart(sink);
        if (wrapAndType) {
            sink.fieldName("type");
            sink.stringValue(tensor.type().toString());
        }
        if (options.shortForm()) {
            emitShortForm(sink);
        } else {
            emitLongForm(sink);
        }
        ensureObjectEnded(sink);
    }

    private void emitShortForm(DataSink sink) {
        if (tensor instanceof IndexedTensor denseTensor) {
            startField("values", sink);
            if (options.hexForDensePart()) {
                sink.stringValue(asHexString(denseTensor));
            } else {
                emitDenseValues(denseTensor, sink);
            }
        } else if (tensor instanceof MappedTensor mapped && tensor.type().dimensions().size() == 1) {
            startField("cells", sink);
            emitSingleDimensionCells(mapped, sink);
        } else if (tensor instanceof MixedTensor mixed && tensor.type().hasMappedDimensions()) {
            boolean singleMapped = tensor.type().dimensions().stream()
                    .filter(TensorType.Dimension::isMapped).count() == 1;
            if (singleMapped) {
                startField("blocks", sink);
                emitLabeledBlocks(mixed, sink);
            } else {
                boolean startedObject = ensureObject(sink);
                startField("blocks", sink);
                emitAddressedBlocks(mixed, sink);
                if (startedObject) {
                    ensureObjectEnded(sink);
                }
            }
        } else {
            startField("cells", sink);
            emitCells(sink);
        }
    }

    private void emitLongForm(DataSink sink)  {
        startField("cells", sink);
        emitCells(sink);
    }

    private void emitCells(DataSink sink)  {
        sink.startArray();
        for (Iterator<Tensor.Cell> i = tensor.cellIterator(); i.hasNext(); ) {
            Tensor.Cell cell = i.next();
            sink.startObject();

            sink.fieldName("address");
            sink.startObject();
            TensorAddress address = cell.getKey();
            for (int j = 0; j < address.size(); j++) {
                sink.fieldName(tensor.type().dimensions().get(j).name());
                sink.stringValue(address.label(j));
            }
            sink.endObject();

            sink.fieldName("value");
            emitValue(cell.getValue(), tensor.type().valueType(), sink);
            sink.endObject();
        }
        sink.endArray();
    }

    private void emitSingleDimensionCells(MappedTensor tensor, DataSink sink)  {
        if (tensor.type().dimensions().size() != 1)
            throw new IllegalStateException("Single dimension encoding requires exactly one dimension");

        sink.startObject();
        tensor.cells().forEach((address, value) -> {
                sink.fieldName(address.label(0));
                emitValue(value, tensor.type().valueType(), sink);
            });
        sink.endObject();
    }

    private void emitDenseValues(IndexedTensor tensor, DataSink sink)  {
        sink.startArray();
        emitDenseValuesRecursive(tensor, sink, new long[tensor.dimensionSizes().dimensions()], 0);
        sink.endArray();
    }

    private void emitDenseValuesRecursive(IndexedTensor tensor, DataSink sink, long[] indexes, int dimension)  {
        DimensionSizes sizes = tensor.dimensionSizes();
        if (indexes.length == 0) {
            emitValue(tensor.get(0), tensor.type().valueType(), sink);
        } else {
            for (indexes[dimension] = 0; indexes[dimension] < sizes.size(dimension); ++indexes[dimension]) {
                if (dimension < (sizes.dimensions() - 1)) {
                    sink.startArray();
                    emitDenseValuesRecursive(tensor, sink, indexes, dimension + 1);
                    sink.endArray();
                } else {
                    emitValue(tensor.get(indexes), tensor.type().valueType(), sink);
                }
            }
        }
    }

    private void emitLabeledBlocks(MixedTensor tensor, DataSink sink)  {
        sink.startObject();
        TensorType denseSubType = tensor.type().indexedSubtype();
        for (var subspace : tensor.getInternalDenseSubspaces()) {
            String label = subspace.sparseAddress.label(0);
            sink.fieldName(label);
            if (options.hexForDensePart()) {
                sink.stringValue(asHexString(subspace.cells.length, denseSubType.valueType(),
                                             i -> subspace.cells[i], i -> (float) subspace.cells[i]));
            } else {
                IndexedTensor denseSubspace = IndexedTensor.Builder.of(denseSubType, subspace.cells).build();
                emitDenseValues(denseSubspace, sink);
            }
        }
        sink.endObject();
    }

    private void emitAddressedBlocks(MixedTensor tensor, DataSink sink)  {
        sink.startArray();
        var mappedDimensions = tensor.type().dimensions().stream()
                .filter(TensorType.Dimension::isMapped)
                .toList();
        TensorType denseSubType = tensor.type().indexedSubtype();

        for (var subspace : tensor.getInternalDenseSubspaces()) {
            sink.startObject();

            sink.fieldName("address");
            sink.startObject();
            for (int i = 0; i < mappedDimensions.size(); i++) {
                sink.fieldName(mappedDimensions.get(i).name());
                sink.stringValue(subspace.sparseAddress.label(i));
            }
            sink.endObject();

            sink.fieldName("values");
            if (options.hexForDensePart()) {
                sink.stringValue(asHexString(subspace.cells.length, denseSubType.valueType(),
                                             i -> subspace.cells[i], i -> (float) subspace.cells[i]));
            } else {
                IndexedTensor denseSubspace = IndexedTensor.Builder.of(denseSubType, subspace.cells).build();
                emitDenseValues(denseSubspace, sink);
            }
            sink.endObject();
        }
        sink.endArray();
    }

    private void emitValue(double value, TensorType.Value valueType, DataSink sink)  {
        switch (valueType) {
            case DOUBLE:
                sink.doubleValue(value);
                break;
            case FLOAT:
            case BFLOAT16:
                sink.floatValue((float)value);
                break;
            case INT8:
                sink.byteValue((byte)value);
                break;
        }
    }

    // Hex encoding methods adapted from JsonFormat
    private static final byte[] hexDigits = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

    private static byte[] asHexString(IndexedTensor tensor) {
        return asHexString(tensor.sizeAsInt(), tensor.type().valueType(),
                           i -> tensor.get(i), i -> tensor.getFloat(i));
    }

    private static byte[] asHexString(int denseSize, TensorType.Value cellType,
                                      java.util.function.Function<Integer, Double> dblSrc,
                                      java.util.function.Function<Integer, Float> fltSrc)
    {
        int nibblesPerCell = switch (cellType) {
            case DOUBLE   -> 16;
            case FLOAT    -> 8;
            case BFLOAT16 -> 4;
            case INT8     -> 2;
        };
        byte[] result = new byte[nibblesPerCell * denseSize];
        int idx = 0;
        for (int i = 0; i < denseSize; i++) {
            long bits = switch (cellType) {
                case DOUBLE   -> Double.doubleToRawLongBits(dblSrc.apply(i));
                case FLOAT    -> Float.floatToRawIntBits(fltSrc.apply(i));
                case BFLOAT16 -> Float.floatToRawIntBits(fltSrc.apply(i)) >>> 16;
                case INT8     -> fltSrc.apply(i).byteValue();
            };
            for (int nibble = nibblesPerCell; nibble-- > 0; ) {
                int digit = (int) (bits >> (4 * nibble)) & 0xF;
                result[idx++] = hexDigits[digit];
            }
        }
        if (idx != result.length) {
            throw new IllegalStateException("Did not fill result["+result.length+"], final idx=" + idx);
        }
        return result;
    }

    private void wrapStart(DataSink sink) {
        if (wrapAndType) {
            ensureObject(sink);
        }
    }
    private void ensureObjectEnded(DataSink sink) {
        if (inObject) {
            sink.endObject();
            inObject = false;
        }
    }
    private void startField(String fieldName, DataSink sink) {
        if (inObject) {
            sink.fieldName(fieldName);
        }
    }
    private boolean ensureObject(DataSink sink) {
        if (inObject) {
            return false;
        }
        sink.startObject();
        inObject = true;
        return true;
    }
}
