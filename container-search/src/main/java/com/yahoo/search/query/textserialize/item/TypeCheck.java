// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.textserialize.item;

import com.yahoo.protect.Validator;

/**
 * @author Tony Vaagenes
 */
public class TypeCheck {
    public static void ensureInstanceOf(Object object, Class<?> c) {
        Validator.ensureInstanceOf(expectationString(c.getName(), object.getClass().getSimpleName()),
                object, c);
    }

    public static void ensureInteger(Object value) {
        ensureInstanceOf(value, Number.class);
        Number number = (Number)value;

        int intValue = number.intValue();
        if (intValue != number.doubleValue())
            throw new IllegalArgumentException("Invalid integer '" + number + "'");
    }

    private static String expectationString(String expected, String got) {
        return "Expected " + expected + ", but got " + got;
    }
}
