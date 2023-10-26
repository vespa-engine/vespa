// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.Before;
import org.junit.Test;

import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * NOTE: Roundtrip serialization (JSON -> DocumentUpdate -> ByteBuffer -> DocumentUpdate -> JSON) of updates is tested in DocumentUpdateJsonSerializerTest.
 *       Consider adding new test cases to that test class instead.
 *
 * @author bratseth
 */
public class SerializationTestCase {

    private DocumentType documentType;

    private Field field;

    @Before
    public void setUp() {
        documentType = new DocumentType("document1");
        field = new Field("field1", DataType.getArray(DataType.STRING));
        documentType.addField(field);
    }

    @Test
    public void testAddSerialization() {
        FieldUpdate update = FieldUpdate.createAdd(field, new StringFieldValue("value1"));
        DocumentSerializer buffer = DocumentSerializerFactory.create6();
        update.serialize(buffer);

        buffer.getBuf().rewind();

        try{
            FileOutputStream fos = new FileOutputStream("src/test/files/addfieldser.dat");
            fos.write(buffer.getBuf().array(), 0, buffer.getBuf().remaining());
            fos.close();
        } catch (Exception e) {}

        FieldUpdate deserializedUpdate = new FieldUpdate(DocumentDeserializerFactory.create6(new DocumentTypeManager(), buffer.getBuf()), documentType);
        assertEquals("'field1' [add value1 1]", deserializedUpdate.toString());
    }

    @Test
    public void testClearSerialization() {
        FieldUpdate update = FieldUpdate.createClear(field);
        DocumentSerializer buffer = DocumentSerializerFactory.create6();
        update.serialize(buffer);

        buffer.getBuf().rewind();
        FieldUpdate deserializedUpdate = new FieldUpdate(DocumentDeserializerFactory.create6(new DocumentTypeManager(), buffer.getBuf()), documentType);

        assertEquals("'field1' [clear]", deserializedUpdate.toString());
    }

}
