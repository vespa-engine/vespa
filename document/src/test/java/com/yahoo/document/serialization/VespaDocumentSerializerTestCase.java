// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.document.CompressionConfig;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 * @author vekterli
 */
@SuppressWarnings("deprecation")
public class VespaDocumentSerializerTestCase {

    @Test
    public void get_serialized_size_uses_latest_serializer() {
        DocumentType docType = new DocumentType("my_type");
        docType.addField("my_str", DataType.STRING);
        docType.addField("my_int", DataType.INT);
        Document doc = new Document(docType, "doc:scheme:");
        doc.setFieldValue("my_str", new StringFieldValue("foo"));
        doc.setFieldValue("my_int", new IntegerFieldValue(69));

        GrowableByteBuffer buf = new GrowableByteBuffer();
        doc.serialize(buf);
        assertEquals(buf.position(), VespaDocumentSerializer42.getSerializedSize(doc));
    }

    @Test
    public void predicate_field_values_are_serialized() {
        DocumentType docType = new DocumentType("my_type");
        Field field = new Field("my_predicate", DataType.PREDICATE);
        docType.addField(field);
        Document doc = new Document(docType, "doc:scheme:");
        PredicateFieldValue predicate = Mockito.mock(PredicateFieldValue.class);
        doc.setFieldValue("my_predicate", predicate);

        DocumentSerializerFactory.create42(new GrowableByteBuffer()).write(doc);
        Mockito.verify(predicate, Mockito.times(1)).serialize(Mockito.same(field), Mockito.any(FieldWriter.class));
    }

    static class CompressionFixture {

        static final String COMPRESSABLE_STRING = "zippy zip mc zippington the 3rd zippy zip";

        final DocumentTypeManager manager;
        final DocumentType docType;
        final StructDataType nestedType;
        final MapDataType mapType;

        CompressionFixture() {
            docType = new DocumentType("map_of_structs");
            docType.getHeaderType().setCompressionConfig(new CompressionConfig(CompressionType.LZ4));

            nestedType = new StructDataType("nested_type");
            nestedType.addField(new Field("str", DataType.STRING));

            mapType = new MapDataType(DataType.STRING, nestedType);
            docType.addField(new Field("map", mapType));

            manager = new DocumentTypeManager();
            manager.registerDocumentType(docType);
        }

        static GrowableByteBuffer asSerialized(Document inputDoc) {
            GrowableByteBuffer buf = new GrowableByteBuffer();
            inputDoc.serialize(buf);
            buf.flip();
            return buf;
        }

        Document roundtripSerialize(Document inputDoc) {
            return manager.createDocument(asSerialized(inputDoc));
        }
    }

    @Test
    public void compressed_map_of_compressed_structs_is_supported() {
        CompressionFixture fixture = new CompressionFixture();

        Document doc = new Document(fixture.docType, "id:foo:map_of_structs::flarn");
        Struct nested = new Struct(fixture.nestedType);
        nested.setFieldValue("str", new StringFieldValue(CompressionFixture.COMPRESSABLE_STRING));

        MapFieldValue<StringFieldValue, Struct> map = new MapFieldValue<StringFieldValue, Struct>(fixture.mapType);
        map.put(new StringFieldValue("foo"), nested);
        map.put(new StringFieldValue("bar"), nested);
        doc.setFieldValue("map", map);

        // Should _not_ throw any deserialization exceptions
        Document result = fixture.roundtripSerialize(doc);
        assertEquals(doc, result);
    }

    @Test
    public void incompressable_structs_are_serialized_without_buffer_size_overhead_bug() {
        CompressionFixture fixture = new CompressionFixture();

        Document doc = new Document(fixture.docType, "id:foo:map_of_structs::flarn");
        Struct nested = new Struct(fixture.nestedType);
        nested.setFieldValue("str", new StringFieldValue(CompressionFixture.COMPRESSABLE_STRING));

        MapFieldValue<StringFieldValue, Struct> map = new MapFieldValue<StringFieldValue, Struct>(fixture.mapType);
        // Only 1 struct added. Not enough redundant information that header struct containing map itself
        // can be compressed.
        map.put(new StringFieldValue("foo"), nested);
        doc.setFieldValue("map", map);

        GrowableByteBuffer buf = CompressionFixture.asSerialized(doc);
        // Explanation of arbitrary value: buffer copy bug meant that incompressable structs were all serialized
        // rounded up to 4096 bytes.
        assertTrue(buf.remaining() < 4096);
    }
}
