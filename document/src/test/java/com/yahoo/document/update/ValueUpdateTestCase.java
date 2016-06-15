// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.update;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;

/**
 * Test case for ValueUpdate class.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class ValueUpdateTestCase extends junit.framework.TestCase {
    public void testUpdateSimple() {
        /** We cannot test much on this level anyway, most stuff in ValueUpdate is package
         * private. Better tests exist in FieldUpdateTestCase. */
        AssignValueUpdate upd = (AssignValueUpdate) ValueUpdate.createAssign(new StringFieldValue("newvalue"));

        assertEquals(ValueUpdate.ValueUpdateClassID.ASSIGN, upd.getValueUpdateClassID());

        FieldValue newValue = upd.getValue();
        assertEquals(new StringFieldValue("newvalue"), newValue);
    }
}
