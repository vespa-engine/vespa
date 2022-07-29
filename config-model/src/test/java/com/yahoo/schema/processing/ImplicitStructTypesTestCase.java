// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.document.*;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class ImplicitStructTypesTestCase extends AbstractSchemaTestCase {
    @Test
    void testRequireThatImplicitStructsAreCreated() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/nextgen/toggleon.sd");
        assertNotNull(schema);

        SDDocumentType docType = schema.getDocument();
        assertNotNull(docType);
        assertStruct(docType, PositionDataType.INSTANCE);
    }

    @Test
    void testRequireThatImplicitStructsAreUsed() throws IOException, ParseException {
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/nextgen/implicitstructtypes.sd");
        assertNotNull(schema);

        SDDocumentType docType = schema.getDocument();
        assertNotNull(docType);

        assertField(docType, "doc_str", DataType.STRING);
        assertField(docType, "doc_str_sum", DataType.STRING);
        assertField(docType, "doc_uri", DataType.URI);
        assertField(docType, "docsum_str", DataType.STRING);
    }

    @SuppressWarnings({ "UnusedDeclaration" })
    private static void assertStruct(SDDocumentType docType, StructDataType expectedStruct) {
        // TODO: When structs are refactored from a static register to a member of the owning document types, this test
        // TODO: must be changed to retrieve struct type from the provided document type.
        StructDataType structType = (StructDataType) docType.getType(expectedStruct.getName()).getStruct();
        assertNotNull(structType);
        for (Field expectedField : expectedStruct.getFields()) {
            Field field = structType.getField(expectedField.getName());
            assertNotNull(field);
            assertEquals(expectedField.getDataType(), field.getDataType());
        }
        assertEquals(expectedStruct.getFieldCount(), structType.getFieldCount());
    }

    private static void assertField(SDDocumentType docType, String fieldName, DataType type) {
        Field field = getSecretField(docType, fieldName); // TODO: get rid of this stupidity
        assertNotNull(field);
        assertEquals(type, field.getDataType());
        assertTrue(field instanceof SDField);
    }

    private static Field getSecretField(SDDocumentType docType, String fieldName) {
        for (Field field : docType.fieldSet()) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }
}
