package com.yahoo.tensor.serialization;

import com.yahoo.slime.Cursor;
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

    /**
     * Serialize the given tensor into JSON format
     */
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

}
