// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test case for ValueUpdate class.
 *
 * @author Einar M R Rosenvinge
 */
public class ValueUpdateTestCase {

    @Test
    public void testUpdateSimple() {
        // We cannot test much on this level anyway, most stuff in ValueUpdate is package
        // private. Better tests exist in FieldUpdateTestCase.
        AssignValueUpdate upd = (AssignValueUpdate) ValueUpdate.createAssign(new StringFieldValue("newvalue"));

        assertEquals(ValueUpdate.ValueUpdateClassID.ASSIGN, upd.getValueUpdateClassID());

        FieldValue newValue = upd.getValue();
        assertEquals(new StringFieldValue("newvalue"), newValue);
    }

}
