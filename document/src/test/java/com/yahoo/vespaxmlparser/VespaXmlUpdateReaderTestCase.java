// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.serialization.DeserializationException;
import com.yahoo.tensor.TensorType;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class VespaXmlUpdateReaderTestCase {

    @Test
    @Ignore
    public void requireThatArithmeticDeserializationValidateValue() throws Exception {
        // tracked in ticket 6675085
        // problem caused by VespaXMLUpdateReader#readArithmetic() parsing value as double
        Field field = new Field("my_field", DataType.BYTE);
        assertThrows(field,
                     "<increment field='my_field' by='-129' />",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column X)");
        assertThrows(field,
                     "<decrement field='my_field' by='-129' />",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column X)");
        assertThrows(field,
                     "<multiply field='my_field' by='-129' />",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column X)");
        assertThrows(field,
                     "<divide field='my_field' by='-129' />",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column X)");
        assertThrows(field,
                     "<alter field='my_field'><increment by='-129' /></alter>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column X)");
    }

    @Test
    @Ignore
    public void requireThatAssignNumericFieldPathValidatesFieldValue() throws Exception {
        // tracked in ticket 6675089
        // problem caused by VespaXMLUpdateReader#read(AssignFieldPathUpdate)
        assertThrows(new Field("my_field", DataType.BYTE),
                     "<assign fieldpath='my_field'>-129</assign>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column X)");
    }

    @Test
    @Ignore
    public void requireThatFieldPathWhereClauseIsValidated() throws Exception {
        // tracked in ticket 6675091
        // problem caused by VespaXMLUpdateReader#read(FieldPathUpdate) not validating where clause
        assertThrows(new Field("my_field", DataType.getArray(DataType.BYTE)),
                     "<remove fieldpath='my_field[$x]' where='my_field[$x] == -129' />",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column X)");
        assertThrows(new Field("my_field", DataType.getMap(DataType.STRING, DataType.BYTE)),
                     "<remove fieldpath='my_field{$x}' where='my_field{$x} == -129' />",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 109)");
    }

    @Test
    public void requireThatDeserializeExceptionIncludesFieldName() throws Exception {
        assertThrows(new Field("my_field", DataType.BYTE),
                     "<assign field='my_field'>-129</assign>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 79)");
    }

    @Test
    public void requireThatArrayItemDeserializeExceptionIncludesFieldName() throws Exception {
        Field field = new Field("my_field", DataType.getArray(DataType.BYTE));
        assertThrows(field,
                     "<assign field='my_field'><item>-129</item></assign>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 83)");
        assertThrows(field,
                     "<assign fieldpath='my_field'><item>-129</item></assign>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 87)");
        assertThrows(field,
                     "<add field='my_field'><item>-129</item></add>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 80)");
        assertThrows(field,
                     "<add fieldpath='my_field'><item>-129</item></add>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 84)");
        assertThrows(field,
                     "<remove field='my_field'><item>-129</item></remove>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 83)");
    }

    @Test
    public void requireThatMapKeyDeserializeExceptionIncludesFieldName() throws Exception {
        Field field = new Field("my_field", DataType.getMap(DataType.BYTE, DataType.STRING));
        assertThrows(field,
                     "<assign field='my_field'><item><key>-129</key><value>foo</value></item></assign>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 87)");
        assertThrows(field,
                     "<assign fieldpath='my_field'><item><key>-129</key><value>foo</value></item></assign>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 91)");
        assertThrows(field,
                     "<add field='my_field'><item><key>-129</key><value>foo</value></item></add>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 84)");
        assertThrows(field,
                     "<add fieldpath='my_field'><item><key>-129</key><value>foo</value></item></add>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 88)");
        assertThrows(field,
                     "<remove field='my_field'><item><key>-129</key><value>foo</value></item></remove>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 87)");
        try {
            readUpdate(field, "<remove fieldpath='my_field{-129}' />");
            fail();
        } catch (NumberFormatException e) {

        }
    }

    @Test
    public void requireThatMapValueDeserializeExceptionIncludesFieldName() throws Exception {
        Field field = new Field("my_field", DataType.getMap(DataType.STRING, DataType.BYTE));
        assertThrows(field,
                     "<assign field='my_field'><item><key>foo</key><value>-129</value></item></assign>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 105)");
        assertThrows(field,
                     "<assign fieldpath='my_field'><item><key>foo</key><value>-129</value></item></assign>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 109)");
        assertThrows(field,
                     "<add field='my_field'><item><key>foo</key><value>-129</value></item></add>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 102)");
        assertThrows(field,
                     "<add fieldpath='my_field'><item><key>foo</key><value>-129</value></item></add>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 106)");
        assertThrows(field,
                     "<remove field='my_field'><item><key>foo</key><value>-129</value></item></remove>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 105)");
    }

    @Test
    public void requireThatStructFieldDeserializeExceptionIncludesFieldName() throws Exception {
        StructDataType structType = new StructDataType("my_struct");
        structType.addField(new Field("my_byte", DataType.BYTE));
        Field field = new Field("my_field", structType);
        assertThrows(field,
                     "<assign field='my_field'><my_byte>-129</my_byte></assign>",
                     "Field 'my_byte': Invalid byte \"-129\". (at line 1, column 89)");
        assertThrows(field,
                     "<assign fieldpath='my_field'><my_byte>-129</my_byte></assign>",
                     "Field 'my_byte': Invalid byte \"-129\". (at line 1, column 93)");
        assertThrows(field,
                     "<add field='my_field'><my_byte>-129</my_byte></add>",
                     "Field 'my_byte': Invalid byte \"-129\". (at line 1, column 86)");
        assertThrows(field,
                     "<add fieldpath='my_field'><my_byte>-129</my_byte></add>",
                     "Field 'my_byte': Invalid byte \"-129\". (at line 1, column 90)");
        assertThrows(field,
                     "<remove field='my_field'><my_byte>-129</my_byte></remove>",
                     "Field 'my_byte': Invalid byte \"-129\". (at line 1, column 89)");
    }

    @Test
    public void requireThatWSetItemDeserializeExceptionIncludesFieldName() throws Exception {
        Field field = new Field("my_field", DataType.getWeightedSet(DataType.BYTE));
        assertThrows(field,
                     "<assign field='my_field'><item>-129</item></assign>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 83)");
        assertThrows(field,
                     "<assign fieldpath='my_field'><item>-129</item></assign>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 87)");
        assertThrows(field,
                     "<add field='my_field'><item>-129</item></add>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 80)");
        assertThrows(field,
                     "<add fieldpath='my_field'><item>-129</item></add>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 84)");
        assertThrows(field,
                     "<remove field='my_field'><item>-129</item></remove>",
                     "Field 'my_field': Invalid byte \"-129\". (at line 1, column 83)");
        try {
            readUpdate(field, "<remove fieldpath='my_field{-129}' />");
            fail();
        } catch (NumberFormatException e) {

        }
    }

    @Test
    public void requireThatCreateIfNonExistentFlagCanBeSpecified() throws Exception {
        {
            assertTrue(readUpdate(true).getCreateIfNonExistent());
            assertFalse(readUpdate(false).getCreateIfNonExistent());
        }
    }

    @Test
    public void requireThatCreateIfNonExistentFlagIsValidated() throws Exception {
        String documentXml = "<update id='doc:scheme:' type='my_type' create-if-non-existent='illegal'></update>";
        try {
            readUpdateHelper(null, documentXml);
            fail();
        } catch (DeserializationException e) {
            assertEquals(printStackTrace(e), "'create-if-non-existent' must be either 'true' or 'false', was 'illegal' (at line 1, column 74)", e.getMessage());
        }
    }

    @Test
    public void requireThatUpdatesForTensorFieldsAreNotSupported() throws Exception {
        assertThrows(new Field("my_tensor", new TensorDataType(TensorType.empty)), "<assign field='my_tensor'></assign>",
                     "Field 'my_tensor': XML input for fields of type TENSOR is not supported. Please use JSON input instead.");
    }

    private static void assertThrows(Field field, String fieldXml, String expected) throws Exception {
        try {
            readUpdate(field, fieldXml);
            fail();
        } catch (DeserializationException e) {
            assertEquals(printStackTrace(e), expected, e.getMessage());
        }
    }

    private static DocumentUpdate readUpdate(Field field, String fieldXml) throws Exception {
        String documentXml = "<update id='doc:scheme:' type='my_type'>" + fieldXml + "</update>";
        return readUpdateHelper(field, documentXml);
    }

    private static DocumentUpdate readUpdate(boolean createIfNonExistent) throws Exception {
        String documentXml = "<update id='doc:scheme:' type='my_type' create-if-non-existent='" + (createIfNonExistent ? "true" : "false") + "'></update>";
        return readUpdateHelper(null, documentXml);
    }

    private static DocumentUpdate readUpdateHelper(Field field, String documentXml) throws Exception {
        DocumentTypeManager docManager = new DocumentTypeManager();
        DocumentType docType = new DocumentType("my_type");
        if (field != null) {
            docType.addField(field);
        }
        docManager.register(docType);

        InputStream in = new ByteArrayInputStream(documentXml.getBytes(StandardCharsets.UTF_8));
        DocumentUpdate doc = new DocumentUpdate(docType, "doc:scheme:");
        VespaXMLUpdateReader reader = new VespaXMLUpdateReader(in, docManager);
        reader.reader.next(); // initialize reader
        reader.read(doc);
        return doc;
    }

    private static String printStackTrace(Throwable t) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        t.printStackTrace(new PrintStream(out));
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
