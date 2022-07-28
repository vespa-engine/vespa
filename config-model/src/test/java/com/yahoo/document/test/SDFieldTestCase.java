// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.test;

import com.yahoo.document.DataType;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.document.SDDocumentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Thomas Gundersen
 */
public class SDFieldTestCase extends AbstractSchemaTestCase {

    @Test
    void testIdSettingConflict() {
        SDDocumentType doc = new SDDocumentType("testdoc");
        doc.addField("one", DataType.STRING, 60);

        doc.addField("two", DataType.STRING, 61);

        try {
            doc.addField("three", DataType.STRING, 60);
            fail("Allowed to set duplicate id");
        }
        catch (IllegalArgumentException e) {
            // Success
        }
    }

    @Test
    void testSettingReservedId() {
        SDDocumentType doc = new SDDocumentType("testdoc");
        try {
            doc.addField("one", DataType.STRING, 127);
            fail("Allowed to set reserved id");
        }
        catch (IllegalArgumentException e) {
            // Success
        }

        try {
            doc.addField("one", DataType.STRING, 100);
            fail("Allowed to set reserved id");
        }
        catch (IllegalArgumentException e) {
            // Success
        }

        try {
            doc.addField("one", DataType.STRING, -1);
            fail("Allowed to set reserved id");
        }
        catch (IllegalArgumentException e) {
            // Success
        }
        doc.addField("one", DataType.STRING);
    }

}
