// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.annotation.AbstractTypesTest;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentDeserializerFactory;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

/**
 * Tests serialization of all versions.
 * <p/>
 * This test tests serialization and deserialization of documents of all
 * supported types.
 * <p/>
 * Serialization is only supported in newest format. Deserialization should work
 * for all formats supported, but only the part that makes sense in the new
 * format. Thus, if new format deprecates a datatype, that datatype, when
 * serializing old versions, must either just be dropped or converted.
 * <p/>
 * Thus, we create document type programmatically, because all old versions need
 * to make sense with current config.
 * <p/>
 * When we create a document programmatically. This is serialized into current
 * version files. When altering the format, after the alteration, copy the
 * current version files to a specific version file and add those to list of
 * files this test checks.
 * <p/>
 * When adding new fields to the documents, use the version tagged with each
 * file to ignore these field for old types.
 *
 * @author arnej27959
 */
public class DocumentSerializationTestCase extends AbstractTypesTest {

    @Test
    public void testSerializationAllVersions() throws IOException {

        DocumentType docInDocType = new DocumentType("docindoc");
        docInDocType.addField(new Field("stringindocfield", DataType.STRING));

        DocumentType docType = new DocumentType("serializetest");
        docType.addField(new Field("floatfield", DataType.FLOAT));
        docType.addField(new Field("stringfield", DataType.STRING));
        docType.addField(new Field("longfield", DataType.LONG));
        docType.addField(new Field("urifield", DataType.URI));
        docType.addField(new Field("intfield", DataType.INT));
        docType.addField(new Field("rawfield", DataType.RAW));
        docType.addField(new Field("doublefield", DataType.DOUBLE));
        docType.addField(new Field("bytefield", DataType.BYTE));
        docType.addField(new Field("boolfield", DataType.BOOL));
        DataType arrayOfFloatDataType = new ArrayDataType(DataType.FLOAT);
        docType.addField(new Field("arrayoffloatfield", arrayOfFloatDataType));
        DataType arrayOfArrayOfFloatDataType = new ArrayDataType(arrayOfFloatDataType);
        docType.addField(new Field("arrayofarrayoffloatfield", arrayOfArrayOfFloatDataType));
        docType.addField(new Field("docfield", DataType.DOCUMENT));
        DataType weightedSetDataType = DataType.getWeightedSet(DataType.STRING, false, false);
        docType.addField(new Field("wsfield", weightedSetDataType));

        DocumentTypeManager docMan = new DocumentTypeManager();
        docMan.register(docInDocType);
        docMan.register(docType);

        String path = "src/test/serializeddocuments/";

        {
            Document doc = new Document(docType, "id:ns:serializetest::http://test.doc.id/");
            doc.setFieldValue("intfield", 5);
            doc.setFieldValue("floatfield", -9.23);
            doc.setFieldValue("stringfield", "This is a string.");
            doc.setFieldValue("longfield", new LongFieldValue(398420092938472983L));
            doc.setFieldValue("doublefield", new DoubleFieldValue(98374532.398820));
            doc.setFieldValue("bytefield", new ByteFieldValue(254));
            doc.setFieldValue("boolfield", new BoolFieldValue(true));
            byte[] rawData = "RAW DATA".getBytes();
            assertEquals(8, rawData.length);
            doc.setFieldValue(docType.getField("rawfield"),new Raw(ByteBuffer.wrap("RAW DATA".getBytes())));
            Document docInDoc = new Document(docInDocType, "id:ns:docindoc::http://doc.in.doc/");
            docInDoc.setFieldValue("stringindocfield", "Elvis is dead");
            doc.setFieldValue(docType.getField("docfield"), docInDoc);
            Array<FloatFieldValue> floatArray = new Array<>(arrayOfFloatDataType);
            floatArray.add(new FloatFieldValue(1.0f));
            floatArray.add(new FloatFieldValue(2.0f));
            doc.setFieldValue("arrayoffloatfield", floatArray);
            WeightedSet<StringFieldValue> weightedSet = new WeightedSet<>(weightedSetDataType);
            weightedSet.put(new StringFieldValue("Weighted 0"), 50);
            weightedSet.put(new StringFieldValue("Weighted 1"), 199);
            doc.setFieldValue("wsfield", weightedSet);

            {
                FileOutputStream fout = new FileOutputStream(path + "document-java-currentversion-uncompressed.dat", false);
                doc.serialize(fout);
                fout.close();
            }
        }

        class TestDoc {

            final String testFile;
            final int version;

            TestDoc(String testFile, int version) {
                this.testFile = testFile;
                this.version = version;
            }
        }

        String cpppath = "src/tests/data/";

        List<TestDoc> tests = new ArrayList<>();
        tests.add(new TestDoc(path + "document-java-currentversion-uncompressed.dat", Document.SERIALIZED_VERSION));
        tests.add(new TestDoc(path + "document-java-v8-uncompressed.dat", 8));
        tests.add(new TestDoc(cpppath + "document-cpp-currentversion-uncompressed.dat", 7));
        tests.add(new TestDoc(cpppath + "document-cpp-v8-uncompressed.dat", 7));
        for (TestDoc test : tests) {
            File f = new File(test.testFile);
            FileInputStream fin = new FileInputStream(f);
            byte[] buffer = new byte[(int)f.length()];
            int pos = 0;
            int remaining = buffer.length;
            while (remaining > 0) {
                int read = fin.read(buffer, pos, remaining);
                assertNotEquals(-1, read);
                pos += read;
                remaining -= read;
            }
            System.err.println("Checking doc from file " + test.testFile);

            Document doc = new Document(DocumentDeserializerFactory.create6(docMan, GrowableByteBuffer.wrap(buffer)));

            System.err.println("Id: " + doc.getId());

            assertEquals(new IntegerFieldValue(5), doc.getFieldValue("intfield"));
            assertEquals(-9.23, ((FloatFieldValue)doc.getFieldValue("floatfield")).getFloat(), 1E-6);
            assertEquals(new StringFieldValue("This is a string."), doc.getFieldValue("stringfield"));
            assertEquals(new LongFieldValue(398420092938472983L), doc.getFieldValue("longfield"));
            assertEquals(98374532.398820, ((DoubleFieldValue)doc.getFieldValue("doublefield")).getDouble(), 1E-6);
            assertEquals(new ByteFieldValue((byte)254), doc.getFieldValue("bytefield"));
            // Todo add cpp serialization
            // assertEquals(new BoolFieldValue(true), doc.getFieldValue("boolfield"));
            ByteBuffer bbuffer = ((Raw)doc.getFieldValue("rawfield")).getByteBuffer();
            if (!Arrays.equals("RAW DATA".getBytes(), bbuffer.array())) {
                System.err.println("Expected 'RAW DATA' but got '" + new String(bbuffer.array()) + "'.");
                fail();
            }
            if (test.version > 6) {
                Document docInDoc = (Document)doc.getFieldValue("docfield");
                assertNotNull(docInDoc);
                assertEquals(new StringFieldValue("Elvis is dead"),
                             docInDoc.getFieldValue("stringindocfield"));
            }
            Array array = (Array)doc.getFieldValue("arrayoffloatfield");
            assertNotNull(array);
            assertEquals(1.0f, ((FloatFieldValue)array.get(0)).getFloat(), 1E-6);
            assertEquals(2.0f, ((FloatFieldValue)array.get(1)).getFloat(), 1E-6);
            WeightedSet wset = (WeightedSet)doc.getFieldValue("wsfield");
            assertNotNull(wset);
            assertEquals(Integer.valueOf(50), wset.get(new StringFieldValue("Weighted 0")));
            assertEquals(Integer.valueOf(199), wset.get(new StringFieldValue("Weighted 1")));
        }
    }

    @Test
    public void testSerializeDeserializeWithAnnotations() throws IOException {
        Document doc = new Document(docType, "id:ns:dokk::bar");

        doc.setFieldValue("age", (byte)123);
        doc.setFieldValue("story", getAnnotatedString());
        doc.setFieldValue("date", 13829297);
        doc.setFieldValue("friend", 2384L);

        GrowableByteBuffer buffer = new GrowableByteBuffer(1024);
        DocumentSerializer serializer = DocumentSerializerFactory.create6(buffer);
        serializer.write(doc);
        buffer.flip();

        FileOutputStream fos = new FileOutputStream("src/tests/data/serializejavawithannotations.dat");
        fos.write(buffer.array(), 0, buffer.limit());
        fos.close();

        DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(man, buffer);
        Document doc2 = new Document(deserializer);

        assertEquals(doc, doc2);
        assertNotSame(doc, doc2);
    }
}
