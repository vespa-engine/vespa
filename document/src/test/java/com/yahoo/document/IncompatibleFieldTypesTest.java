// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for ticket 6394548
 */
public class IncompatibleFieldTypesTest {
    private DataType arrayOfStrings;
    private StructDataType struct;
    private StructuredFieldValue root;

    @Before
    public void setUp() {
        arrayOfStrings = new ArrayDataType(DataType.STRING);
        struct = new StructDataType("fancypants");
        struct.addField(new Field("stringarray", arrayOfStrings));
        DataType weightedSetOfStrings = DataType.getWeightedSet(DataType.STRING, false, false);
        struct.addField(new Field("stringws", weightedSetOfStrings));

        root = struct.createFieldValue();
        root.setFieldValue("stringarray", arrayOfStrings.createFieldValue());
        root.setFieldValue("stringws", weightedSetOfStrings.createFieldValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddingIncompatibleFieldToArrayFails() {
        System.out.println(root.getFieldValue("stringarray").getDataType().createFieldValue().getClass().getName());
        System.out.println(root.getFieldValue("stringarray").getDataType().createFieldValue().getDataType().toString());

        ((Array)root.getFieldValue("stringarray")).add(new IntegerFieldValue(1234));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddingIncompatibleFieldToWeightedSetFails() {
        System.out.println(root.getFieldValue("stringws").getDataType().createFieldValue().getClass().getName());
        System.out.println(root.getFieldValue("stringws").getDataType().createFieldValue().getDataType().toString());

        ((WeightedSet<FieldValue>)root.getFieldValue("stringws")).put(new IntegerFieldValue(1234), 100);
    }
}
