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
 * See a http://docs.vespa.ai/documentation/reference/document-json-put-format.html#tensor
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
        if ( cells.type() != Type.ARRAY)
            throw new IllegalArgumentException("Excepted 'cells' to contain an array, not " + cells.type());
        cells.traverse((ArrayTraverser) (__, cell) -> decodeCell(cell, builder));
    }

    private static void decodeCell(Inspector cell, Tensor.Builder builder) {
        TensorAddress address = decodeAddress(cell.field("address"), builder.type());

        Inspector value = cell.field("value");
        if (value.type() != Type.LONG && value.type() != Type.DOUBLE)
            throw new IllegalArgumentException("Excepted a cell to contain a numeric value called 'value'");

        builder.cell(address, value.asDouble());
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
        if (values.type() != Type.ARRAY)
            throw new IllegalArgumentException("Excepted 'blocks' to contain an array, not " + values.type());

        MixedTensor.BoundBuilder mixedBuilder = (MixedTensor.BoundBuilder) builder;

        values.traverse((ArrayTraverser) (__, value) -> decodeBlock(value, mixedBuilder));
    }

    private static void decodeBlock(Inspector block, MixedTensor.BoundBuilder mixedBuilder) {
        if (block.type() != Type.OBJECT)
            throw new IllegalArgumentException("Expected an item in a 'blocks' array to be an object, not " + block.type());

        TensorAddress mappedAddress = decodeAddress(block.field("address"), mixedBuilder.type().mappedSubtype());

        Inspector valuesField = block.field("values");
        if (valuesField.type() != Type.ARRAY)
            throw new IllegalArgumentException("Expected a block to contain a 'values' array");
        double[] values = new double[(int)mixedBuilder.denseSubspaceSize()];
        valuesField.traverse((ArrayTraverser) (index, value) -> values[index] = decodeNumeric(value));

        mixedBuilder.block(mappedAddress, values);
    }

    private static TensorAddress decodeAddress(Inspector addressField, TensorType type) {
        if (addressField.type() != Type.OBJECT)
            throw new IllegalArgumentException("Expected an 'address' object, not " + addressField.type());
        TensorAddress.Builder builder = new TensorAddress.Builder(type);
        addressField.traverse((ObjectTraverser) (dimension, label) -> builder.add(dimension, label.asString()));
        return builder.build();
    }

    private static double decodeNumeric(Inspector numericField) {
        if (numericField.type() != Type.LONG && numericField.type() != Type.DOUBLE)
            throw new IllegalArgumentException("Excepted a number, not " + numericField.type());
        return numericField.asDouble();
    }

}
