// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.lang.MutableInteger;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.MappedTensor;
import com.yahoo.tensor.MixedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.functions.ConstantTensor;
import com.yahoo.tensor.functions.Slice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Writes tensors on the JSON format used in Vespa tensor document fields:
 * A JSON map containing a 'cells' or 'values' array.
 * See <a href="https://docs.vespa.ai/en/reference/document-json-format.html">
 * https://docs.vespa.ai/en/reference/document-json-format.html</a>
 *
 * @author bratseth
 */
public class JsonFormat {

    /**
     * Serializes the given tensor value into JSON format.
     *
     * @param tensor the tensor to serialize
     * @param shortForm whether to encode in a short type-dependent format
     * @param directValues whether to encode values directly, or wrapped in am object containing "type" and "cells"
     */
    public static byte[] encode(Tensor tensor, boolean shortForm, boolean directValues) {
        Slime slime = new Slime();
        Cursor root = null;
        if ( ! directValues) {
            root = slime.setObject();
            root.setString("type", tensor.type().toString());
        }

        if (shortForm) {
            if (tensor instanceof IndexedTensor denseTensor) {
                // Encode as nested lists if indexed tensor
                Cursor parent = root == null ? slime.setArray() : root.setArray("values");
                encodeValues(denseTensor, parent, new long[denseTensor.dimensionSizes().dimensions()], 0);
            } else if (tensor instanceof MappedTensor && tensor.type().dimensions().size() == 1) {
                // Short form for a single mapped dimension
                Cursor parent = root == null ? slime.setObject() : root.setObject("cells");
                encodeSingleDimensionCells((MappedTensor) tensor, parent);
            } else if (tensor instanceof MixedTensor &&
                       tensor.type().dimensions().stream().anyMatch(TensorType.Dimension::isMapped)) {
                // Short form for a mixed tensor
                boolean singleMapped = tensor.type().dimensions().stream().filter(TensorType.Dimension::isMapped).count() == 1;
                Cursor parent = root == null ? ( singleMapped ? slime.setObject() : slime.setArray() )
                                             : ( singleMapped ? root.setObject("blocks") : root.setArray("blocks"));
                encodeBlocks((MixedTensor) tensor, parent);
            } else {
                // default to standard cell address output
                Cursor parent = root == null ? slime.setArray() : root.setArray("cells");
                encodeCells(tensor, parent);
            }

            return com.yahoo.slime.JsonFormat.toJsonBytes(slime);
        }
        else {
            Cursor parent = root == null ? slime.setArray() : root.setArray("cells");
            encodeCells(tensor, parent);
        }
        return com.yahoo.slime.JsonFormat.toJsonBytes(slime);
    }

    /** Serializes the given tensor value into JSON format, in long format, wrapped in an object containing "cells" only. */
    public static byte[] encode(Tensor tensor) {
        return encode(tensor, false, false);
    }

    /**
     * Serializes the given tensor type and value into JSON format.
     *
     * @deprecated use #encode(#Tensor, boolean, boolean)
     */
    @Deprecated // TODO: Remove on Vespa 9
    public static byte[] encodeWithType(Tensor tensor) {
        return encode(tensor, false, false);
    }

    /**
     * Serializes the given tensor type and value into a short-form JSON format.
     *
     * @deprecated use #encode(#Tensor, boolean, boolean)
     */
    @Deprecated // TODO: Remove on Vespa 9
    public static byte[] encodeShortForm(Tensor tensor) {
        return encode(tensor, true, false);
    }

    private static void encodeCells(Tensor tensor, Cursor cellsArray) {
        for (Iterator<Tensor.Cell> i = tensor.cellIterator(); i.hasNext(); ) {
            Tensor.Cell cell = i.next();
            Cursor cellObject = cellsArray.addObject();
            encodeAddress(tensor.type(), cell.getKey(), cellObject.setObject("address"));
            setValue("value", cell.getValue(), tensor.type().valueType(), cellObject);
        }
    }

    private static void encodeSingleDimensionCells(MappedTensor tensor, Cursor cells) {
        if (tensor.type().dimensions().size() > 1)
            throw new IllegalStateException("JSON encode of mapped tensor can only contain a single dimension");
        tensor.cells().forEach((k,v) -> setValue(k.label(0), v, tensor.type().valueType(), cells));
    }

    private static void encodeAddress(TensorType type, TensorAddress address, Cursor addressObject) {
        for (int i = 0; i < address.size(); i++)
            addressObject.setString(type.dimensions().get(i).name(), address.label(i));
    }

    private static void encodeValues(IndexedTensor tensor, Cursor cursor, long[] indexes, int dimension) {
        DimensionSizes sizes = tensor.dimensionSizes();
        if (indexes.length == 0) {
            addValue(tensor.get(0), tensor.type().valueType(), cursor);
        } else {
            for (indexes[dimension] = 0; indexes[dimension] < sizes.size(dimension); ++indexes[dimension]) {
                if (dimension < (sizes.dimensions() - 1)) {
                    encodeValues(tensor, cursor.addArray(), indexes, dimension + 1);
                } else {
                    addValue(tensor.get(indexes), tensor.type().valueType(), cursor);
                }
            }
        }
    }

    private static void encodeBlocks(MixedTensor tensor, Cursor cursor) {
        var mappedDimensions = tensor.type().dimensions().stream().filter(d -> d.isMapped())
                .map(d -> TensorType.Dimension.mapped(d.name())).toList();
        if (mappedDimensions.size() < 1) {
            throw new IllegalArgumentException("Should be ensured by caller");
        }

        // Create tensor type for mapped dimensions subtype
        TensorType mappedSubType = new TensorType.Builder(mappedDimensions).build();

        // Find all unique indices for the mapped dimensions
        Set<TensorAddress> denseSubSpaceAddresses = new HashSet<>();
        tensor.cellIterator().forEachRemaining((cell) -> {
            denseSubSpaceAddresses.add(subAddress(cell.getKey(), mappedSubType, tensor.type()));
        });

        // Slice out dense subspace of each and encode dense subspace as a list
        for (TensorAddress denseSubSpaceAddress : denseSubSpaceAddresses) {
            IndexedTensor denseSubspace = (IndexedTensor) sliceSubAddress(tensor, denseSubSpaceAddress, mappedSubType);

            if (mappedDimensions.size() == 1) {
                encodeValues(denseSubspace, cursor.setArray(denseSubSpaceAddress.label(0)), new long[denseSubspace.dimensionSizes().dimensions()], 0);
            } else {
                Cursor block = cursor.addObject();
                encodeAddress(mappedSubType, denseSubSpaceAddress, block.setObject("address"));
                encodeValues(denseSubspace, block.setArray("values"), new long[denseSubspace.dimensionSizes().dimensions()], 0);
            }

        }
    }

    private static void addValue(double value, TensorType.Value valueType, Cursor cursor) {
        if (valueType == TensorType.Value.INT8)
            cursor.addLong((long)value);
        else
            cursor.addDouble(value);
    }

    private static void setValue(String field, double value, TensorType.Value valueType, Cursor cursor) {
        if (valueType == TensorType.Value.INT8)
            cursor.setLong(field, (long)value);
        else
            cursor.setDouble(field, value);
    }

    private static TensorAddress subAddress(TensorAddress address, TensorType subType, TensorType origType) {
        TensorAddress.Builder builder = new TensorAddress.Builder(subType);
        for (TensorType.Dimension dim : subType.dimensions()) {
            builder.add(dim.name(), address.label(origType.indexOfDimension(dim.name()).
                    orElseThrow(() -> new IllegalStateException("Could not find mapped dimension index"))));
        }
        return builder.build();
    }

    private static Tensor sliceSubAddress(Tensor tensor, TensorAddress subAddress, TensorType subType) {
        List<Slice.DimensionValue<Name>> sliceDims = new ArrayList<>(subAddress.size());
        for (int i = 0; i < subAddress.size(); ++i) {
            sliceDims.add(new Slice.DimensionValue<>(subType.dimensions().get(i).name(), subAddress.label(i)));
        }
        return new Slice<>(new ConstantTensor<>(tensor), sliceDims).evaluate();
    }

    /** Deserializes the given tensor from JSON format */
    // NOTE: This must be kept in sync with com.yahoo.document.json.readers.TensorReader in the document module
    public static Tensor decode(TensorType type, byte[] jsonTensorValue) {
        Tensor.Builder builder = Tensor.Builder.of(type);
        Inspector root = new JsonDecoder().decode(new Slime(), jsonTensorValue).get();

        if (root.field("cells").valid() && ! primitiveContent(root.field("cells")))
            decodeCells(root.field("cells"), builder);
        else if (root.field("values").valid() && builder.type().dimensions().stream().allMatch(d -> d.isIndexed()))
            decodeValues(root.field("values"), builder);
        else if (root.field("blocks").valid())
            decodeBlocks(root.field("blocks"), builder);
        else
            decodeDirectValue(root, builder);
        return builder.build();
    }

    private static boolean primitiveContent(Inspector cellsValue) {
        if (cellsValue.type() == Type.DOUBLE) return true;
        if (cellsValue.type() == Type.LONG) return true;
        if (cellsValue.type() == Type.ARRAY && cellsValue.entries() > 0 &&
            ( cellsValue.entry(0).type() == Type.DOUBLE || cellsValue.entry(0).type() == Type.LONG)) return true;
        return false;
    }

    private static void decodeCells(Inspector cells, Tensor.Builder builder) {
        if (cells.type() == Type.ARRAY)
            cells.traverse((ArrayTraverser) (__, cell) -> decodeCell(cell, builder));
        else if (cells.type() == Type.OBJECT)
            cells.traverse((ObjectTraverser) (key, value) -> decodeSingleDimensionCell(key, value, builder));
        else
            throw new IllegalArgumentException("Excepted 'cells' to contain an array or object, not " + cells.type());
    }

    private static void decodeCell(Inspector cell, Tensor.Builder builder) {
        TensorAddress address = decodeAddress(cell.field("address"), builder.type());

        Inspector value = cell.field("value");
        if (value.type() != Type.LONG && value.type() != Type.DOUBLE)
            throw new IllegalArgumentException("Excepted a cell to contain a numeric value called 'value'");

        builder.cell(address, value.asDouble());
    }

    private static void decodeSingleDimensionCell(String key, Inspector value, Tensor.Builder builder) {
        builder.cell(asAddress(key, builder.type()), decodeNumeric(value));
    }

    private static void decodeValues(Inspector values, Tensor.Builder builder) {
        decodeValues(values, builder, new MutableInteger(0));
    }

    private static void decodeValues(Inspector values, Tensor.Builder builder, MutableInteger index) {
        if ( ! (builder instanceof IndexedTensor.BoundBuilder indexedBuilder))
            throw new IllegalArgumentException("An array of values can only be used with a dense tensor. Use a map instead");
        if (values.type() == Type.STRING) {
            double[] decoded = decodeHexString(values.asString(), builder.type().valueType());
            if (decoded.length == 0)
                throw new IllegalArgumentException("The values string does not contain any values");
            for (int i = 0; i < decoded.length; i++) {
                indexedBuilder.cellByDirectIndex(i, decoded[i]);
            }
            return;
        }
        if (values.type() != Type.ARRAY)
            throw new IllegalArgumentException("Excepted values to be an array, not " + values.type());
        if (values.entries() == 0)
            throw new IllegalArgumentException("The values array does not contain any values");

        values.traverse((ArrayTraverser) (__, value) -> {
            if (value.type() == Type.ARRAY)
                decodeValues(value, builder, index);
            else if (value.type() == Type.LONG || value.type() == Type.DOUBLE)
                indexedBuilder.cellByDirectIndex(index.next(), value.asDouble());
            else
                throw new IllegalArgumentException("Excepted the values array to contain numbers or nested arrays, not " + value.type());
        });
    }

    private static void decodeBlocks(Inspector values, Tensor.Builder builder) {
        if ( ! (builder instanceof MixedTensor.BoundBuilder mixedBuilder))
            throw new IllegalArgumentException("Blocks of values can only be used with mixed (sparse and dense) tensors." +
                                               "Use an array of cell values instead.");

        if (values.type() == Type.ARRAY)
            values.traverse((ArrayTraverser) (__, value) -> decodeBlock(value, mixedBuilder));
        else if (values.type() == Type.OBJECT)
            values.traverse((ObjectTraverser) (key, value) -> decodeSingleDimensionBlock(key, value, mixedBuilder));
        else
            throw new IllegalArgumentException("Excepted the block to contain an array or object, not " + values.type());
    }

    private static void decodeBlock(Inspector block, MixedTensor.BoundBuilder mixedBuilder) {
        if (block.type() != Type.OBJECT)
            throw new IllegalArgumentException("Expected an item in a blocks array to be an object, not " + block.type());
        mixedBuilder.block(decodeAddress(block.field("address"), mixedBuilder.type().mappedSubtype()),
                           decodeValues(block.field("values"), mixedBuilder));
    }

    /** Decodes a tensor value directly at the root, where the format is decided by the tensor type. */
    private static void decodeDirectValue(Inspector root, Tensor.Builder builder) {
        boolean hasIndexed = builder.type().dimensions().stream().anyMatch(TensorType.Dimension::isIndexed);
        boolean hasMapped = builder.type().dimensions().stream().anyMatch(TensorType.Dimension::isMapped);

        if (isArrayOfObjects(root))
            decodeCells(root, builder);
        else if ( ! hasMapped)
            decodeValues(root, builder);
        else if (hasMapped && hasIndexed)
            decodeBlocks(root, builder);
        else
            decodeCells(root, builder);
    }

    private static boolean isArrayOfObjects(Inspector inspector) {
        if (inspector.type() != Type.ARRAY) return false;
        if (inspector.entries() == 0) return false;
        Inspector firstItem = inspector.entry(0);
        if (firstItem.type() == Type.ARRAY) return isArrayOfObjects(firstItem);
        return firstItem.type() == Type.OBJECT;
    }

    private static void decodeSingleDimensionBlock(String key, Inspector value, MixedTensor.BoundBuilder mixedBuilder) {
        if (value.type() != Type.ARRAY)
            throw new IllegalArgumentException("Expected an item in a blocks array to be an array, not " + value.type());
        mixedBuilder.block(asAddress(key, mixedBuilder.type().mappedSubtype()),
                           decodeValues(value, mixedBuilder));
    }

    private static byte decodeHex(String input, int index) {
        int d = Character.digit(input.charAt(index), 16);
        if (d < 0) {
            throw new IllegalArgumentException("Invalid digit '"+input.charAt(index)+"' at index "+index+" in input "+input);
        }
        return (byte)d;
    }

    private static double[] decodeHexStringAsBytes(String input) {
        int l = input.length() / 2;
        double[] result = new double[l];
        int idx = 0;
        for (int i = 0; i < l; i++) {
            byte v = decodeHex(input, idx++);
            v <<= 4;
            v += decodeHex(input, idx++);
            result[i] = v;
        }
        return result;
    }

    private static double[] decodeHexStringAsBFloat16s(String input) {
        int l = input.length() / 4;
        double[] result = new double[l];
        int idx = 0;
        for (int i = 0; i < l; i++) {
            int v = decodeHex(input, idx++);
            v <<= 4; v += decodeHex(input, idx++);
            v <<= 4; v += decodeHex(input, idx++);
            v <<= 4; v += decodeHex(input, idx++);
            v <<= 16;
            result[i] = Float.intBitsToFloat(v);
        }
        return result;
    }

    private static double[] decodeHexStringAsFloats(String input) {
        int l = input.length() / 8;
        double[] result = new double[l];
        int idx = 0;
        for (int i = 0; i < l; i++) {
            int v = 0;
            for (int j = 0; j < 8; j++) {
                v <<= 4;
                v += decodeHex(input, idx++);
            }
            result[i] = Float.intBitsToFloat(v);
        }
        return result;
    }

    private static double[] decodeHexStringAsDoubles(String input) {
        int l = input.length() / 16;
        double[] result = new double[l];
        int idx = 0;
        for (int i = 0; i < l; i++) {
            long v = 0;
            for (int j = 0; j < 16; j++) {
                v <<= 4;
                v += decodeHex(input, idx++);
            }
            result[i] = Double.longBitsToDouble(v);
        }
        return result;
    }

    public static double[] decodeHexString(String input, TensorType.Value valueType) {
        return switch (valueType) {
            case INT8 -> decodeHexStringAsBytes(input);
            case BFLOAT16 -> decodeHexStringAsBFloat16s(input);
            case FLOAT -> decodeHexStringAsFloats(input);
            case DOUBLE -> decodeHexStringAsDoubles(input);
        };
    }

    private static double[] decodeValues(Inspector valuesField, MixedTensor.BoundBuilder mixedBuilder) {
        double[] values = new double[(int)mixedBuilder.denseSubspaceSize()];
        if (valuesField.type() == Type.ARRAY) {
            if (valuesField.entries() == 0) {
                throw new IllegalArgumentException("The block value array does not contain any values");
            }
            valuesField.traverse((ArrayTraverser) (index, value) -> values[index] = decodeNumeric(value));
        } else if (valuesField.type() == Type.STRING) {
            double[] decoded = decodeHexString(valuesField.asString(), mixedBuilder.type().valueType());
            if (decoded.length == 0) {
                throw new IllegalArgumentException("The block value string does not contain any values");
            }
            for (int i = 0; i < decoded.length; i++) {
                values[i] = decoded[i];
            }
        } else {
            throw new IllegalArgumentException("Expected a block to contain an array of values");
        }
        return values;
    }

    private static TensorAddress decodeAddress(Inspector addressField, TensorType type) {
        if (addressField.type() != Type.OBJECT)
            throw new IllegalArgumentException("Expected an 'address' object, not " + addressField.type());
        TensorAddress.Builder builder = new TensorAddress.Builder(type);
        addressField.traverse((ObjectTraverser) (dimension, label) -> builder.add(dimension, label.asString()));
        return builder.build();
    }

    private static TensorAddress asAddress(String label, TensorType type) {
        if (type.dimensions().size() != 1)
            throw new IllegalArgumentException("Expected a tensor with a single dimension but got " + type);
        return new TensorAddress.Builder(type).add(type.dimensions().get(0).name(), label).build();
    }

    private static double decodeNumeric(Inspector numericField) {
        if (numericField.type() != Type.LONG && numericField.type() != Type.DOUBLE)
            throw new IllegalArgumentException("Excepted a number, not " + numericField.type());
        return numericField.asDouble();
    }

}
