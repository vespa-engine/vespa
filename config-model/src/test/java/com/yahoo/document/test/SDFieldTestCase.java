// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.test;

import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.document.SDDocumentType;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * @author Thomas Gundersen
 */
public class SDFieldTestCase extends SearchDefinitionTestCase {

    @Test
    public void testIdSettingConflict() {
        SDDocumentType doc = new SDDocumentType("testdoc");
        doc.addField("one", DataType.STRING, false, 60);

        doc.addField("two", DataType.STRING, false, 61);

        try {
            doc.addField("three", DataType.STRING, false, 60);
            fail("Allowed to set duplicate id");
        }
        catch (IllegalArgumentException e) {
            // Success
        }
    }

    @Test
    public void testSettingReservedId() {
        SDDocumentType doc = new SDDocumentType("testdoc");
        try {
            doc.addField("one", DataType.STRING, false, 127);
            fail("Allowed to set reserved id");
        }
        catch (IllegalArgumentException e) {
            // Success
        }

        try {
            doc.addField("one", DataType.STRING, false, 100);
            fail("Allowed to set reserved id");
        }
        catch (IllegalArgumentException e) {
            // Success
        }

        try {
            doc.addField("one", DataType.STRING, false, -1);
            fail("Allowed to set reserved id");
        }
        catch (IllegalArgumentException e) {
            // Success
        }
        doc.addField("one", DataType.STRING);
    }

}
