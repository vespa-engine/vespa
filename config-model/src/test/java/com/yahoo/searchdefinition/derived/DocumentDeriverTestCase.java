// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.*;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.document.SDDocumentType;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests deriving of documentmanager
 *
 * @author Mathias Moelster Lidal
 */
public class DocumentDeriverTestCase extends AbstractExportingTestCase {

    @Test
    public void testStructTypesNotUsed() {
        String root = "src/test/derived/documentderiver/";

        List<String> files = new ArrayList<>();
        files.add(root + "sombrero.sd");

        File toDir = new File("temp/structtypesnotused/");
        toDir.mkdir();

        DocumentTypeManager dtm = new DocumentTypeManager();
        int numBuiltInTypes = dtm.getDataTypes().size();
        dtm.configure("file:" + toDir.getPath() + "/documentmanager.cfg");

        DocumentType webDocType = dtm.getDocumentType("webdoc");
        assertNotNull(webDocType);

        assertEquals(1, webDocType.fieldSet().size());
        Field html = webDocType.getField("html");
        assertNotNull(html);
        assertEquals(DataType.STRING, html.getDataType());

        assertEquals(numBuiltInTypes + 8, dtm.getDataTypes().size());

        {
            StructDataType keyvalue = (StructDataType) dtm.getDataType("keyvalue");
            assertNotNull(keyvalue);
            assertEquals(2, keyvalue.getFields().size());
            Field key = keyvalue.getField("key");
            assertNotNull(key);
            assertEquals(DataType.STRING, key.getDataType());
            Field value = keyvalue.getField("value");
            assertNotNull(value);
            assertEquals(DataType.STRING, value.getDataType());
        }
        {
            StructDataType tagvalue = (StructDataType) dtm.getDataType("tagvalue");
            assertNotNull(tagvalue);
            assertEquals(2, tagvalue.getFields().size());
            Field name = tagvalue.getField("name");
            assertNotNull(name);
            assertEquals(DataType.STRING, name.getDataType());
            Field attributes = tagvalue.getField("attributes");
            assertNotNull(attributes);
            assertTrue(attributes.getDataType() instanceof ArrayDataType);
            assertEquals(dtm.getDataType("keyvalue"), ((ArrayDataType) attributes.getDataType()).getNestedType());
        }
        {
            StructDataType wordform = (StructDataType) dtm.getDataType("wordform");
            assertNotNull(wordform);
            assertEquals(3, wordform.getFields().size());
            Field kind = wordform.getField("kind");
            assertNotNull(kind);
            assertEquals(DataType.INT, kind.getDataType());
            Field form = wordform.getField("form");
            assertNotNull(form);
            assertEquals(DataType.STRING, form.getDataType());
            Field weight = wordform.getField("weight");
            assertNotNull(weight);
            assertEquals(DataType.FLOAT, weight.getDataType());
        }

    }

}
