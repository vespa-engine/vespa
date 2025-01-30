// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.util.Iterator;
import java.util.function.Function;

/**
 * Writes tensors on the JSON format used in Vespa tensor document fields:
 * A JSON map containing a 'cells' or 'values' array.
 * See <a href="https://docs.vespa.ai/en/reference/document-json-format.html">
 * https://docs.vespa.ai/en/reference/document-json-format.html</a>
 *
 * @author bratseth
 */
public class JsonFormat {

    /** Options for encode */
    public record EncodeOptions(boolean shortForm, boolean directValues, boolean hexForDensePart) {
        // TODO - consider "compact" flag
    }
    /**
     * Serializes the given tensor value into JSON format.
     *
     * @param tensor the tensor to serialize
     * @param shortForm whether to encode in a short type-dependent format
     * @param directValues whether to encode values directly, or wrapped in am object containing "type" and "cells"
     */
    public static byte[] encode(Tensor tensor, boolean shortForm, boolean directValues) {
        return encode(tensor, new EncodeOptions(shortForm, directValues, false));
    }
    public static byte[] encode(Tensor tensor, boolean shortForm, boolean directValues, boolean hexForDense) {
        return encode(tensor, new EncodeOptions(shortForm, directValues, hexForDense));
    }

    private static abstract class OptionalKey {
        abstract Cursor setObject(String key);
        abstract Cursor setArray(String key);
        abstract void setString(String key, String value);
        static OptionalKey of(Slime s) {
            return new OptionalKey() {
                final Slime target = s;
                Cursor setObject(String key) { return target.setObject(); }
                Cursor setArray(String key) { return target.setArray(); }
                void setString(String key, String value) { target.setString(value); }
            };
        }
        static OptionalKey of(Cursor c) {
            return new OptionalKey() {
                final private Cursor target = c;
                Cursor setObject(String key) { return target.setObject(key); }
                Cursor setArray(String key) { return target.setArray(key); }
                void setString(String key, String value) { target.setString(key, value); }
            };
        }
    }

    /**
     * Serializes the given tensor value into JSON format.
     *
     * @param tensor the tensor to serialize
     * @param options format options for short/long, wrapped/direct, etc
     */
    public static byte[] encode(Tensor tensor, EncodeOptions options) {
        Slime slime = new Slime();
        var target = OptionalKey.of(slime);
        Cursor root = null;
        if ( ! options.directValues()) {
            root = slime.setObject();
            root.setString("type", tensor.type().toString());
            target = OptionalKey.of(root);
        }

        if (options.shortForm()) {
            if (tensor instanceof IndexedTensor denseTensor) {
                if (options.hexForDensePart()) {
                    target.setString("values", asHexString(denseTensor));
                } else {
                    // Encode as nested arrays if indexed tensor
                    Cursor parent = target.setArray("values");
                    encodeDenseValues(denseTensor, parent);
                }
            } else if (tensor instanceof MappedTensor mapped && tensor.type().dimensions().size() == 1) {
                // Short form for a single mapped dimension
                Cursor parent = target.setObject("cells");
                encodeSingleDimensionCells(mapped, parent);
            } else if (tensor instanceof MixedTensor mixed && tensor.type().hasMappedDimensions()) {
                // Short form for a mixed tensor
                boolean singleMapped = tensor.type().dimensions().stream().filter(TensorType.Dimension::isMapped).count() == 1;
                if (singleMapped) {
                    encodeLabeledBlocks(mixed, target.setObject("blocks"), options.hexForDensePart());
                } else {
                    encodeAddressedBlocks(mixed, target.setArray("blocks"), options.hexForDensePart());
                }
            } else {
                // default to standard cell address output
                Cursor parent = target.setArray("cells");
                encodeCells(tensor, parent);
            }

            return com.yahoo.slime.JsonFormat.toJsonBytes(slime);
        }
        else {
            Cursor parent = target.setArray("cells");
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

    private static final char[] hexDigits = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };


    private static String asHexString(IndexedTensor tensor) {
        return asHexString(tensor.sizeAsInt(),
                           tensor.type().valueType(),
                           i -> tensor.get(i),
                           i -> tensor.getFloat(i));
    }

    private static String asHexString(int denseSize,
                                      TensorType.Value cellType,
                                      Function<Integer, Double> dblSrc,
                                      Function<Integer, Float> fltSrc)
    {
        StringBuilder buf = new StringBuilder();
        switch (cellType) {
            case DOUBLE:
                for (int i = 0; i < denseSize; i++) {
                    double d = dblSrc.apply(i);
                    long bits = Double.doubleToRawLongBits(d);
                    for (int nibble = 16; nibble-- > 0; ) {
                        int digit = (int) (bits >> (4 * nibble)) & 0xF;
                        buf.append(hexDigits[digit]);
                    }
                }
                break;
            case FLOAT:
                for (int i = 0; i < denseSize; i++) {
                    float f = fltSrc.apply(i);
                    int bits = Float.floatToRawIntBits(f);
                    for (int nibble = 8; nibble-- > 0; ) {
                        int digit = (bits >> (4 * nibble)) & 0xF;
                        buf.append(hexDigits[digit]);
                    }
                }
                break;
            case BFLOAT16:
                for (int i = 0; i < denseSize; i++) {
                    float f = fltSrc.apply(i);
                    int bits = Float.floatToRawIntBits(f);
                    for (int nibble = 8; nibble-- > 4; ) {
                        int digit = (bits >> (4 * nibble)) & 0xF;
                        buf.append(hexDigits[digit]);
                    }
                }
                break;
            case INT8:
                for (int i = 0; i < denseSize; i++) {
                    byte bits = fltSrc.apply(i).byteValue();
                    for (int nibble = 2; nibble-- > 0; ) {
                        int digit = (bits >> (4 * nibble)) & 0xF;
                        buf.append(hexDigits[digit]);
                    }
                }
                break;
        }
        return buf.toString();
    }

    private static void encodeDenseValues(IndexedTensor tensor, Cursor target) {
        encodeValues(tensor, target, new long[tensor.dimensionSizes().dimensions()], 0);
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

    private static void encodeLabeledBlocks(MixedTensor tensor, Cursor cursor, boolean hexForDensePart) {
        for (var subspace : tensor.getInternalDenseSubspaces()) {
            String label = subspace.sparseAddress.label(0);
            if (hexForDensePart) {
                cursor.setString(label, asHexString(subspace.cells.length,
                                                    tensor.type().valueType(),
                                                    i -> subspace.cells[i],
                                                    i -> (float)subspace.cells[i]));
            } else {
                TensorType denseSubType = tensor.type().indexedSubtype();
                IndexedTensor denseSubspace = IndexedTensor.Builder.of(denseSubType, subspace.cells).build();
                var target = cursor.setArray(label);
                encodeDenseValues(denseSubspace, target);
            }
        }
    }

    private static void encodeAddressedBlocks(MixedTensor tensor, Cursor cursor, boolean hexForDensePart) {
        var mappedDimensions = tensor.type().dimensions().stream()
                .filter(TensorType.Dimension::isMapped)
                .toList();
        if (mappedDimensions.isEmpty()) {
            throw new IllegalArgumentException("Should be ensured by caller");
        }
        // Create tensor type for mapped dimensions subtype
        TensorType mappedSubType = new TensorType.Builder(mappedDimensions).build();
        TensorType denseSubType = tensor.type().indexedSubtype();
        for (var subspace : tensor.getInternalDenseSubspaces()) {
            Cursor block = cursor.addObject();
            encodeAddress(mappedSubType, subspace.sparseAddress, block.setObject("address"));
            if (hexForDensePart) {
                cursor.setString("values", asHexString(subspace.cells.length,
                                                    tensor.type().valueType(),
                                                    i -> subspace.cells[i],
                                                    i -> (float)subspace.cells[i]));
            } else {
                IndexedTensor denseSubspace = IndexedTensor.Builder.of(denseSubType, subspace.cells).build();
                var target = block.setArray("values");
                encodeDenseValues(denseSubspace, target);
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

    /** Deserializes the given tensor from JSON format */
    // NOTE: This must be kept in sync with com.yahoo.document.json.readers.TensorReader in the document module
    public static Tensor decode(TensorType type, byte[] jsonTensorValue) {
        Tensor.Builder builder = Tensor.Builder.of(type);
        Inspector root = new JsonDecoder().decode(new Slime(), jsonTensorValue).get();

        if (root.field("cells").valid() && ! primitiveContent(root.field("cells")))
            decodeCells(root.field("cells"), builder);
        else if (root.field("values").valid() && ! builder.type().hasMappedDimensions())
            decodeValuesAtTop(root.field("values"), builder);
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
        if (value.valid()) {
            builder.cell(address, decodeNumeric(value));
        } else {
            throw new IllegalArgumentException("Excepted a cell to contain a numeric value called 'value'");
        }
    }

    private static void decodeSingleDimensionCell(String key, Inspector value, Tensor.Builder builder) {
        builder.cell(asAddress(key, builder.type()), decodeNumeric(value));
    }

    private static void decodeValuesAtTop(Inspector values, Tensor.Builder builder) {
        decodeNestedValues(values, builder, new MutableInteger(0));
    }

    private static void decodeNestedValues(Inspector values, Tensor.Builder builder, MutableInteger index) {
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
                decodeNestedValues(value, builder, index);
            else if (value.type() == Type.LONG || value.type() == Type.DOUBLE || value.type() == Type.STRING || value.type() == Type.NIX)
                indexedBuilder.cellByDirectIndex(index.next(), decodeNumeric(value));
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
                           decodeValuesInBlock(block.field("values"), mixedBuilder));
    }

    /** Decodes a tensor value directly at the root, where the format is decided by the tensor type. */
    private static void decodeDirectValue(Inspector root, Tensor.Builder builder) {
        boolean hasIndexed = builder.type().hasIndexedDimensions();
        boolean hasMapped = builder.type().hasMappedDimensions();

        if (isArrayOfObjects(root))
            decodeCells(root, builder);
        else if ( ! hasMapped)
            decodeValuesAtTop(root, builder);
        else if (hasIndexed)
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
        if (value.type() != Type.ARRAY && value.type() != Type.STRING)
            throw new IllegalArgumentException("Expected an item in a blocks array to be an array, not " + value.type());
        mixedBuilder.block(asAddress(key, mixedBuilder.type().mappedSubtype()),
                           decodeValuesInBlock(value, mixedBuilder));
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

    private static void decodeMaybeNestedValuesInBlock(Inspector arrayField, double[] target, MutableInteger index) {
        if (arrayField.entries() == 0) {
            throw new IllegalArgumentException("The block value array does not contain any values");
        }
        arrayField.traverse((ArrayTraverser) (__, value) -> {
                if (value.type() == Type.ARRAY) {
                    decodeMaybeNestedValuesInBlock(value, target, index);
                } else {
                    target[index.next()] = decodeNumeric(value);
                }
            });
    }

    private static double[] decodeValuesInBlock(Inspector valuesField, MixedTensor.BoundBuilder mixedBuilder) {
        double[] values = new double[(int)mixedBuilder.denseSubspaceSize()];
        if (valuesField.type() == Type.ARRAY) {
            decodeMaybeNestedValuesInBlock(valuesField, values, new MutableInteger(0));
        } else if (valuesField.type() == Type.STRING) {
            double[] decoded = decodeHexString(valuesField.asString(), mixedBuilder.type().valueType());
            if (decoded.length == 0) {
                throw new IllegalArgumentException("The block value string does not contain any values");
            }
            System.arraycopy(decoded, 0, values, 0, decoded.length);
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
        if (numericField.type() == Type.DOUBLE || numericField.type() == Type.LONG) {
            return numericField.asDouble();
        }
        if (numericField.type() == Type.STRING) {
            return decodeNumberString(numericField.asString());
        }
        if (numericField.type() == Type.NIX) {
            return Double.NaN;
        }
        throw new IllegalArgumentException("Excepted a number, not " + numericField.type());
    }

    public static double decodeNumberString(String input) {
        String s = input.toLowerCase();
        if (s.equals("infinity") || s.equals("+infinity") || s.equals("inf") || s.equals("+inf")) {
            return Double.POSITIVE_INFINITY;
        }
        if (s.equals("-infinity") || s.equals("-inf")) {
            return Double.NEGATIVE_INFINITY;
        }
        if (s.equals("nan") || s.equals("+nan")) {
            return Double.NaN;
        }
        if (s.equals("-nan")) {
            return Math.copySign(Double.NaN, -1.0); // or Double.longBitsToDouble(0xfff8000000000000L);
        }
        return Double.parseDouble(input);
    }

}
