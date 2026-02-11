// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.predicate.BinaryFormat;
import com.yahoo.document.predicate.Conjunction;
import com.yahoo.document.predicate.FeatureRange;
import com.yahoo.document.predicate.FeatureSet;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.document.serialization.DeserializationException;
import com.yahoo.tensor.TensorType;
import org.junit.Test;

import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * @author Simon Thoresen Hult
 */
public class VespaXmlFieldReaderTestCase {

    @Test
    public void requireThatPredicateFieldValuesCanBeRead() throws Exception {
        assertReadable(new Conjunction(new FeatureSet("foo", "bar"),
                                       new FeatureRange("baz", 6L, 9L)));
    }

    @Test
    public void requireThatArrayItemDeserializeExceptionIncludesFieldName() throws Exception {
        assertThrows(new Field("my_field", DataType.getArray(DataType.BYTE)),
                     "<item>-129</item>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 74)");
    }

    @Test
    public void requireThatMapKeyDeserializeExceptionIncludesFieldName() throws Exception {
        assertThrows(new Field("my_field", DataType.getMap(DataType.BYTE, DataType.STRING)),
                     "<item><key>-129</key><value>foo</value></item>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 78)");
    }

    @Test
    public void requireThatMapValueDeserializeExceptionIncludesFieldName() throws Exception {
        assertThrows(new Field("my_field", DataType.getMap(DataType.STRING, DataType.BYTE)),
                     "<item><key>foo</key><value>-129</value></item>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 96)");
    }

    @Test
    public void requireThatStructFieldDeserializeExceptionIncludesFieldName() throws Exception {
        StructDataType structType = new StructDataType("my_struct");
        structType.addField(new Field("my_byte", DataType.BYTE));
        assertThrows(new Field("my_field", structType),
                     "<my_byte>-129</my_byte>",
                     "Field 'my_byte': Invalid byte \"-129\". (at line 1, column 80)");
    }

    @Test
    public void requireThatWSetItemDeserializeExceptionIncludesFieldName() throws Exception {
        assertThrows(new Field("my_field", DataType.getWeightedSet(DataType.BYTE)),
                     "<item>-129</item>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 74)");
    }

    @Test
    public void requireThatPutsForTensorFieldsAreNotSupported() throws Exception {
        assertThrows(new Field("my_tensor", new TensorDataType(TensorType.empty)), "",
                     "Field 'my_tensor': XML input for fields of type TENSOR is not supported. Please use JSON input instead.");
    }

    private class MockedReaderFixture {
        public DocumentTypeManager mgr;
        public DocumentType docType;
        public XMLStreamReader xmlReader;
        public VespaXMLFieldReader fieldReader;

        public MockedReaderFixture() {
            mgr = new DocumentTypeManager();
            mgr.register(PositionDataType.INSTANCE);
            docType = new DocumentType("my_doc");
            docType.addField("my_pos", PositionDataType.INSTANCE);
            mgr.registerDocumentType(docType);

            xmlReader = mock(XMLStreamReader.class);
            fieldReader = new VespaXMLFieldReader(xmlReader, mgr);
        }

        public void assertReadPositionEquals(int x, int y) {
            Struct pos = new Struct(PositionDataType.INSTANCE);
            fieldReader.read(docType.getField("my_pos"), pos);

            assertEquals(new IntegerFieldValue(x), pos.getFieldValue(PositionDataType.FIELD_X));
            assertEquals(new IntegerFieldValue(y), pos.getFieldValue(PositionDataType.FIELD_Y));
        }
    }

    @Test
    public void requireThatPositionFieldCanBeReadInSingleEvent() throws Exception {
        MockedReaderFixture fixture = new MockedReaderFixture();
        XMLStreamReader xmlReader = fixture.xmlReader;

        when(xmlReader.getAttributeCount()).thenReturn(0);
        when(xmlReader.hasNext()).thenReturn(true, true, false);
        when(xmlReader.next()).thenReturn(
                XMLStreamReader.CHARACTERS, XMLStreamReader.END_ELEMENT);
        when(xmlReader.getText()).thenReturn("E3;N4");

        fixture.assertReadPositionEquals(3000000, 4000000);
    }

    @Test
    public void requireThatPositionFieldCanBeReadAcrossMultipleEvents() throws Exception {
        MockedReaderFixture fixture = new MockedReaderFixture();
        XMLStreamReader xmlReader = fixture.xmlReader;

        when(xmlReader.getAttributeCount()).thenReturn(0);
        when(xmlReader.hasNext()).thenReturn(true, true, true, true, false);
        when(xmlReader.next()).thenReturn(
                XMLStreamReader.CHARACTERS, XMLStreamReader.CHARACTERS,
                XMLStreamReader.CHARACTERS, XMLStreamReader.END_ELEMENT);
        when(xmlReader.getText()).thenReturn("E3;", "N", "4");

        fixture.assertReadPositionEquals(3000000, 4000000);
    }

    private static void assertThrows(Field field, String fieldXml, String expected) throws Exception {
        DocumentTypeManager docManager = new DocumentTypeManager();
        DocumentType docType = new DocumentType("my_type");
        docType.addField(field);
        docManager.register(docType);

        String documentXml = "<document id='id:ns:my_type::' type='my_type'><" + field.getName() + ">" +
                             fieldXml + "</" + field.getName() + "></document>";
        InputStream in = new ByteArrayInputStream(documentXml.getBytes(StandardCharsets.UTF_8));
        Document doc = new Document(docType, "id:ns:my_type::");
        try {
            new VespaXMLFieldReader(in, docManager).read(null, doc);
            fail();
        } catch (DeserializationException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    private static void assertReadable(Predicate predicate) throws Exception {
        assertRead(predicate,
                   "<document id='id:ns:my_type::' type='my_type'>" +
                   "  <my_predicate>" + predicate + "</my_predicate>" +
                   "</document>");
        assertRead(predicate,
                   "<document id='id:ns:my_type::' type='my_type'>" +
                   "  <my_predicate binaryencoding='base64'>" +
                           Base64.getMimeEncoder().encodeToString(BinaryFormat.encode(predicate)) +
                   "  </my_predicate>" +
                   "</document>");
    }

    private static void assertRead(Predicate expected, String documentXml) throws Exception {
        DocumentTypeManager docManager = new DocumentTypeManager();
        DocumentType docType = new DocumentType("my_type");
        docType.addField("my_predicate", DataType.PREDICATE);
        docManager.register(docType);

        InputStream in = new ByteArrayInputStream(documentXml.getBytes(StandardCharsets.UTF_8));
        Document doc = new Document(docType, "id:ns:my_type::");
        new VespaXMLFieldReader(in, docManager).read(null, doc);
        FieldValue value = doc.getFieldValue("my_predicate");
        assertTrue(value instanceof PredicateFieldValue);
        assertEquals(expected, ((PredicateFieldValue)value).getPredicate());
    }
}
