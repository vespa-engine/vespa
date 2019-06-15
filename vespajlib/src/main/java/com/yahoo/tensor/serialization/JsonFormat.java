// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;

/**
 * Writes tensors on the JSON format used in Vespa tensor document fields:
 * A JSON map containing a 'cells' array.
 * See http://docs.vespa.ai/documentation/reference/document-json-put-format.html#tensor
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
    public static Tensor decode(TensorType type, byte[] jsonTensorValue) {
        Tensor.Builder tensorBuilder = Tensor.Builder.of(type);
        Inspector root = new JsonDecoder().decode(new Slime(), jsonTensorValue).get();
        Inspector cells = root.field("cells");
        if ( cells.type() != Type.ARRAY)
            throw new IllegalArgumentException("Excepted an array item named 'cells' at the top level");
        cells.traverse((ArrayTraverser) (__, cell) -> decodeCell(cell, tensorBuilder.cell()));
        return tensorBuilder.build();
    }

    private static void decodeCell(Inspector cell, Tensor.Builder.CellBuilder cellBuilder) {
        Inspector address = cell.field("address");
        if ( address.type() != Type.OBJECT)
            throw new IllegalArgumentException("Excepted a cell to contain an object called 'address'");
        address.traverse((ObjectTraverser) (dimension, label) -> cellBuilder.label(dimension, label.asString()));

        Inspector value = cell.field("value");
        if (value.type() != Type.LONG && value.type() != Type.DOUBLE)
            throw new IllegalArgumentException("Excepted a cell to contain a numeric value called 'value'");
        cellBuilder.value(value.asDouble());
    }

}
