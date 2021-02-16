// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.lang.MutableInteger;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.MixedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;

/**
 * Writes tensors on the JSON format used in Vespa tensor document fields:
 * A JSON map containing a 'cells' or 'values' array.
 * See <a href="https://docs.vespa.ai/en/reference/document-json-format.html">
 * https://docs.vespa.ai/en/reference/document-json-format.html</a>
 *
 * @author bratseth
 */
public class JsonFormat {

    /** Serializes the given tensor value into JSON format */
    public static byte[] encode(Tensor tensor) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        encodeCells(tensor, root);
        return com.yahoo.slime.JsonFormat.toJsonBytes(slime);
    }

    /** Serializes the given tensor type and value into JSON format */
    public static byte[] encodeWithType(Tensor tensor) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("type", tensor.type().toString());
        encodeCells(tensor, root);
        return com.yahoo.slime.JsonFormat.toJsonBytes(slime);
    }

    private static void encodeCells(Tensor tensor, Cursor rootObject) {
        Cursor cellsArray = rootObject.setArray("cells");
        for (Iterator<Tensor.Cell> i = tensor.cellIterator(); i.hasNext(); ) {
            Tensor.Cell cell = i.next();
            Cursor cellObject = cellsArray.addObject();
            encodeAddress(tensor.type(), cell.getKey(), cellObject.setObject("address"));
            cellObject.setDouble("value", cell.getValue());
        }
    }

    private static void encodeAddress(TensorType type, TensorAddress address, Cursor addressObject) {
        for (int i = 0; i < address.size(); i++)
            addressObject.setString(type.dimensions().get(i).name(), address.label(i));
    }

    /** Deserializes the given tensor from JSON format */
    // NOTE: This must be kept in sync with com.yahoo.document.json.readers.TensorReader in the document module
    public static Tensor decode(TensorType type, byte[] jsonTensorValue) {
        Tensor.Builder builder = Tensor.Builder.of(type);
        Inspector root = new JsonDecoder().decode(new Slime(), jsonTensorValue).get();

        if (root.field("cells").valid())
            decodeCells(root.field("cells"), builder);
        else if (root.field("values").valid())
            decodeValues(root.field("values"), builder);
        else if (root.field("blocks").valid())
            decodeBlocks(root.field("blocks"), builder);
        else if (builder.type().dimensions().stream().anyMatch(d -> d.isIndexed())) // sparse can be empty
            throw new IllegalArgumentException("Expected a tensor value to contain either 'cells' or 'values'");
        return builder.build();
    }

    private static void decodeCells(Inspector cells, Tensor.Builder builder) {
        if ( cells.type() == Type.ARRAY)
            cells.traverse((ArrayTraverser) (__, cell) -> decodeCell(cell, builder));
        else if (cells.type() == Type.OBJECT)
            cells.traverse((ObjectTraverser) (key, value) -> decodeSingleDimensionCell(key, value, builder));
        else
            throw new IllegalArgumentException("Excepted 'cells' to contain an array or obejct, not " + cells.type());
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
        if ( ! (builder instanceof IndexedTensor.BoundBuilder))
            throw new IllegalArgumentException("The 'values' field can only be used with dense tensors. " +
                                               "Use 'cells' or 'blocks' instead");
        if ( values.type() != Type.ARRAY)
            throw new IllegalArgumentException("Excepted 'values' to contain an array, not " + values.type());

        IndexedTensor.BoundBuilder indexedBuilder = (IndexedTensor.BoundBuilder)builder;
        MutableInteger index = new MutableInteger(0);
        values.traverse((ArrayTraverser) (__, value) -> {
            if (value.type() != Type.LONG && value.type() != Type.DOUBLE)
                throw new IllegalArgumentException("Excepted the values array to contain numbers, not " + value.type());
            indexedBuilder.cellByDirectIndex(index.next(), value.asDouble());
        });
    }

    private static void decodeBlocks(Inspector values, Tensor.Builder builder) {
        if ( ! (builder instanceof MixedTensor.BoundBuilder))
            throw new IllegalArgumentException("The 'blocks' field can only be used with mixed tensors with bound dimensions. " +
                                               "Use 'cells' or 'values' instead");
        MixedTensor.BoundBuilder mixedBuilder = (MixedTensor.BoundBuilder) builder;

        if (values.type() == Type.ARRAY)
            values.traverse((ArrayTraverser) (__, value) -> decodeBlock(value, mixedBuilder));
        else if (values.type() == Type.OBJECT)
            values.traverse((ObjectTraverser) (key, value) -> decodeSingleDimensionBlock(key, value, mixedBuilder));
        else
            throw new IllegalArgumentException("Excepted 'blocks' to contain an array or object, not " + values.type());
    }

    private static void decodeBlock(Inspector block, MixedTensor.BoundBuilder mixedBuilder) {
        if (block.type() != Type.OBJECT)
            throw new IllegalArgumentException("Expected an item in a 'blocks' array to be an object, not " + block.type());
        mixedBuilder.block(decodeAddress(block.field("address"), mixedBuilder.type().mappedSubtype()),
                           decodeValues(block.field("values"), mixedBuilder));
    }

    private static void decodeSingleDimensionBlock(String key, Inspector value, MixedTensor.BoundBuilder mixedBuilder) {
        if (value.type() != Type.ARRAY)
            throw new IllegalArgumentException("Expected an item in a 'blocks' array to be an object, not " + value.type());
        mixedBuilder.block(asAddress(key, mixedBuilder.type().mappedSubtype()),
                           decodeValues(value, mixedBuilder));
    }

    private static double[] decodeValues(Inspector valuesField, MixedTensor.BoundBuilder mixedBuilder) {
        if (valuesField.type() != Type.ARRAY)
            throw new IllegalArgumentException("Expected a block to contain a 'values' array");
        double[] values = new double[(int)mixedBuilder.denseSubspaceSize()];
        valuesField.traverse((ArrayTraverser) (index, value) -> values[index] = decodeNumeric(value));
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
