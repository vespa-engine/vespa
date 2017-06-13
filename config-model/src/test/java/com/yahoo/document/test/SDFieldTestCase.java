// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.test;

import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
   @author  <a href="thomasg@yahoo-inc.com>Thomas Gundersen</a>
*/
public class SDFieldTestCase extends SearchDefinitionTestCase {
    @Test
    public void testIdSettingConflict() {
        SDDocumentType doc=new SDDocumentType("testdoc");
        SDField one=(SDField) doc.addField("one", DataType.STRING, false, 60);

        SDField two=(SDField) doc.addField("two", DataType.STRING, false, 61);

        try {
            SDField three=(SDField) doc.addField("three", DataType.STRING, false, 60);
            fail("Allowed to set duplicate id");
        }
        catch (IllegalArgumentException e) {
            // Success
        }
    }
    @Test
    public void testSettingReservedId() {
        SDDocumentType doc=new SDDocumentType("testdoc");
        try {
            SDField one=(SDField) doc.addField("one", DataType.STRING, false, 127);
            fail("Allowed to set reserved id");
        }
        catch (IllegalArgumentException e) {
            // Success
        }

        try {
            SDField one=(SDField) doc.addField("one", DataType.STRING, false, 100);
            fail("Allowed to set reserved id");
        }
        catch (IllegalArgumentException e) {
            // Success
        }

        try {
            SDField one=(SDField) doc.addField("one", DataType.STRING, false, -1);
            fail("Allowed to set reserved id");
        }
        catch (IllegalArgumentException e) {
            // Success
        }
        SDField one= doc.addField("one", DataType.STRING);
    }

}
