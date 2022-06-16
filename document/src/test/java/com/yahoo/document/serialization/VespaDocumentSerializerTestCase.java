// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 * @author vekterli
 */
public class VespaDocumentSerializerTestCase {

    @Test
    public void get_serialized_size_uses_latest_serializer() {
        DocumentType docType = new DocumentType("my_type");
        docType.addField("my_str", DataType.STRING);
        docType.addField("my_int", DataType.INT);
        Document doc = new Document(docType, "id:ns:my_type::");
        doc.setFieldValue("my_str", new StringFieldValue("foo"));
        doc.setFieldValue("my_int", new IntegerFieldValue(69));

        GrowableByteBuffer buf = new GrowableByteBuffer();
        doc.serialize(buf);
        assertEquals(buf.position(), VespaDocumentSerializerHead.getSerializedSize(doc));
    }

    @Test
    public void predicate_field_values_are_serialized() {
        DocumentType docType = new DocumentType("my_type");
        Field field = new Field("my_predicate", DataType.PREDICATE);
        docType.addField(field);
        Document doc = new Document(docType, "id:ns:my_type::");
        PredicateFieldValue predicate = Mockito.mock(PredicateFieldValue.class);
        doc.setFieldValue("my_predicate", predicate);

        DocumentSerializerFactory.create6(new GrowableByteBuffer()).write(doc);
        Mockito.verify(predicate, Mockito.times(1)).serialize(Mockito.same(field), Mockito.any(FieldWriter.class));
    }

}
