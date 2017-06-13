// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.serialization.*;

import java.io.FileOutputStream;

/**
 * @author bratseth
 */
public class SerializationTestCase extends junit.framework.TestCase {

    private DocumentType documentType;

    private Field field;

    public SerializationTestCase(String name) {
        super(name);
    }

    public void setUp() {
        documentType=new DocumentType("document1");
        field=new Field("field1", DataType.getArray(DataType.STRING));
        documentType.addField(field);
    }

    public void testAddSerialization() {
        FieldUpdate update=FieldUpdate.createAdd(field, new StringFieldValue("value1"));
        DocumentSerializer buffer = DocumentSerializerFactory.create42();
        update.serialize(buffer);

        buffer.getBuf().rewind();

        try{
            FileOutputStream fos = new FileOutputStream("src/test/files/addfieldser.dat");
            fos.write(buffer.getBuf().array(), 0, buffer.getBuf().remaining());
            fos.close();
        } catch (Exception e) {}

        FieldUpdate deserializedUpdate = new FieldUpdate(DocumentDeserializerFactory.create42(new DocumentTypeManager(), buffer.getBuf()), documentType, Document.SERIALIZED_VERSION);
        assertEquals("'field1' [add value1 1]", deserializedUpdate.toString());
    }

    public void testClearSerialization() {
        FieldUpdate update=FieldUpdate.createClear(field);
        DocumentSerializer buffer = DocumentSerializerFactory.create42();
        update.serialize(buffer);

        buffer.getBuf().rewind();
        FieldUpdate deserializedUpdate = new FieldUpdate(DocumentDeserializerFactory.create42(new DocumentTypeManager(), buffer.getBuf()), documentType, Document.SERIALIZED_VERSION);

        assertEquals("'field1' [clear]", deserializedUpdate.toString());
    }

}
