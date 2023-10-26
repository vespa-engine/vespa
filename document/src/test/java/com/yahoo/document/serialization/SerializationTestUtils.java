// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.Document;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.io.GrowableByteBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;

import static org.junit.Assert.assertEquals;

/**
 * Helper class with utils used in serialization and deserialization test cases.
 *
 * @author geirst
 */
public class SerializationTestUtils {

    public static byte[] serializeDocument(Document doc) {
        GrowableByteBuffer out = new GrowableByteBuffer();
        DocumentSerializerFactory.create6(out).write(doc);
        out.flip();
        byte[] buf = new byte[out.remaining()];
        out.get(buf);
        return buf;
    }

    public static Document deserializeDocument(byte[] buf, TestDocumentFactory factory) {
        Document document = factory.createDocument();
        DocumentDeserializerFactory.create6(factory.typeManager(), new GrowableByteBuffer(ByteBuffer.wrap(buf))).read(document);
        return document;
    }

    public static void assertFieldInDocumentSerialization(TestDocumentFactory documentFactory, String fieldName,
                                                          FieldValue serializableFieldValue) {
        Document document = documentFactory.createDocument();
        document.setFieldValue(fieldName, serializableFieldValue);
        byte[] buf = serializeDocument(document);
        Document deserializedDocument = deserializeDocument(buf, documentFactory);
        assertEquals(document, deserializedDocument);
        assertEquals(serializableFieldValue, deserializedDocument.getFieldValue(fieldName));
    }

    public static void assertSerializationMatchesCpp(String binaryFilesFolder, String fileName,
                                                     Document document, TestDocumentFactory factory) throws IOException {
        byte[] buf = serializeDocument(document);
        Files.write(Paths.get(binaryFilesFolder, fileName + "__java.new"), buf,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(Paths.get(binaryFilesFolder, fileName + "__java.new"),
                   Paths.get(binaryFilesFolder, fileName + "__java"), StandardCopyOption.ATOMIC_MOVE);

        assertDeserializeFromFile(Paths.get(binaryFilesFolder, fileName + "__java"), document, factory);
        assertDeserializeFromFile(Paths.get(binaryFilesFolder, fileName + "__cpp"), document, factory);
    }

    private static void assertDeserializeFromFile(Path path, Document document, TestDocumentFactory factory) throws IOException {
        byte[] buf = Files.readAllBytes(path);
        Document deserializedDocument = deserializeDocument(buf, factory);
        assertEquals(path.toString(), document, deserializedDocument);
    }

}
