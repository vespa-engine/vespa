// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.document.DocumentTypeManager;
import static com.yahoo.config.model.test.TestUtil.joinLines;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author arnej
 */
public class ConvertIntermediateTestCase {

    @Test
    void can_convert_minimal_schema() throws Exception {
        String input = joinLines
                ("schema foo {",
                        "  document foo {",
                        "  }",
                        "}");
        var collection = new IntermediateCollection();
        ParsedSchema schema = collection.addSchemaFromString(input);
        assertEquals("foo", schema.getDocument().name());
        var docMan = new DocumentTypeManager();
        var converter = new ConvertSchemaCollection(collection, docMan);
        converter.convertTypes();
        var dt = docMan.getDocumentType("foo");
        assertTrue(dt != null);
    }

    @Test
    void can_convert_schema_files() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromFile("src/test/derived/deriver/child.sd");
        collection.addSchemaFromFile("src/test/derived/deriver/grandparent.sd");
        collection.addSchemaFromFile("src/test/derived/deriver/parent.sd");
        assertEquals(collection.getParsedSchemas().size(), 3);
        var docMan = new DocumentTypeManager();
        var converter = new ConvertSchemaCollection(collection, docMan);
        converter.convertTypes();
        var dt = docMan.getDocumentType("child");
        assertTrue(dt != null);
        dt = docMan.getDocumentType("parent");
        assertTrue(dt != null);
        dt = docMan.getDocumentType("grandparent");
        assertTrue(dt != null);
    }

    @Test
    void can_convert_structs_and_annotations() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromFile("src/test/converter/child.sd");
        collection.addSchemaFromFile("src/test/converter/other.sd");
        collection.addSchemaFromFile("src/test/converter/parent.sd");
        collection.addSchemaFromFile("src/test/converter/grandparent.sd");
        var docMan = new DocumentTypeManager();
        var converter = new ConvertSchemaCollection(collection, docMan);
        converter.convertTypes();
        var dt = docMan.getDocumentType("child");
        assertTrue(dt != null);
        for (var parent : dt.getInheritedTypes()) {
            System.err.println("dt " + dt.getName() + " inherits from " + parent.getName());
        }
        for (var field : dt.fieldSetAll()) {
            System.err.println("dt " + dt.getName() + " contains field " + field.getName() + " of type " + field.getDataType());
        }
        dt = docMan.getDocumentType("parent");
        assertTrue(dt != null);
        for (var parent : dt.getInheritedTypes()) {
            System.err.println("dt " + dt.getName() + " inherits from " + parent.getName());
        }
        for (var field : dt.fieldSetAll()) {
            System.err.println("dt " + dt.getName() + " contains field " + field.getName() + " of type " + field.getDataType());
        }
        dt = docMan.getDocumentType("grandparent");
        assertTrue(dt != null);
        for (var parent : dt.getInheritedTypes()) {
            System.err.println("dt " + dt.getName() + " inherits from " + parent.getName());
        }
        for (var field : dt.fieldSetAll()) {
            System.err.println("dt " + dt.getName() + " contains field " + field.getName() + " of type " + field.getDataType());
        }
        dt = docMan.getDocumentType("other");
        assertTrue(dt != null);
        for (var parent : dt.getInheritedTypes()) {
            System.err.println("dt " + dt.getName() + " inherits from " + parent.getName());
        }
        for (var field : dt.fieldSetAll()) {
            System.err.println("dt " + dt.getName() + " contains field " + field.getName() + " of type " + field.getDataType());
        }
    }
}
