// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * @author Thomas Gundersen
 */
public class FieldTestCase {

    @Test
    public void testIdSettingConflict() {
        DocumentType doc = new DocumentType("testdoc");
        Field one = doc.addField("one", DataType.STRING);
        one.setId(60, doc);

        Field two = doc.addField("two", DataType.STRING);
        two.setId(61, doc);

        try {
            Field three = doc.addField("three", DataType.STRING);
            three.setId(60, doc);
            fail("Allowed to set duplicate id");
        } catch (IllegalArgumentException e) {
            // Success
        }
    }

    @Test
    public void testSettingReservedId() {
        DocumentType doc = new DocumentType("testdoc");
        try {
            Field one = doc.addField("one", DataType.STRING);
            one.setId(127, doc);
            fail("Allowed to set reserved id");
        } catch (IllegalArgumentException e) {
            // Success
        }

        try {
            Field one = doc.addField("one", DataType.STRING);
            one.setId(100, doc);
            fail("Allowed to set reserved id");
        } catch (IllegalArgumentException e) {
            // Success
        }

        try {
            Field one = doc.addField("one", DataType.STRING);
            one.setId(-1, doc);
            fail("Allowed to set reserved id");
        } catch (IllegalArgumentException e) {
            // Success
        }
        doc.removeField("one");
        Field one = doc.addField("one", DataType.STRING);
    }

}
