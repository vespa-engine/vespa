// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;

/**
 * Writes tensors on the JSON format used in Vespa tensor document fields:
 * A JSON map containing a 'cells' array.
 * See http://docs.vespa.ai/documentation/reference/document-json-put-format.html#tensor
 */
// TODO: We should probably move reading of this format from the document module to here
public class JsonFormat {

    /** Serializes the given tensor into JSON format */
    public static byte[] encode(Tensor tensor) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor cellsArray = root.setArray("cells");
        for (Iterator<Tensor.Cell> i = tensor.cellIterator(); i.hasNext(); ) {
            Tensor.Cell cell = i.next();
            Cursor cellObject = cellsArray.addObject();
            encodeAddress(tensor.type(), cell.getKey(), cellObject.setObject("address"));
            cellObject.setDouble("value", cell.getValue());
        }
        return com.yahoo.slime.JsonFormat.toJsonBytes(slime);
    }

    private static void encodeAddress(TensorType type, TensorAddress address, Cursor addressObject) {
        for (int i = 0; i < address.size(); i++)
            addressObject.setString(type.dimensions().get(i).name(), address.label(i));
    }

    /** Deserializes the given tensor from JSON format */
    // TODO: Add explicit validation (valid() checks) below
    public static Tensor decode(TensorType type, byte[] jsonTensorValue) {
        Tensor.Builder tensorBuilder = Tensor.Builder.of(type);
        Inspector root = new JsonDecoder().decode(new Slime(), jsonTensorValue).get();
        Inspector cells = root.field("cells");
        cells.traverse((ArrayTraverser) (__, cell) -> decodeCell(cell, tensorBuilder.cell()));
        return tensorBuilder.build();
    }

    private static void decodeCell(Inspector cell, Tensor.Builder.CellBuilder cellBuilder) {
        cell.field("address").traverse((ObjectTraverser) (dimension, label) -> cellBuilder.label(dimension, label.asString()));
        cellBuilder.value(cell.field("value").asDouble());
    }

}
