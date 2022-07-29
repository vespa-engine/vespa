// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Hack to support both junit4 and junit5
 *
 * @author bjorncs
 */
public class JunitCompat {
    private JunitCompat() {}

    public static <T> void assertEquals(T l, T r) {
        List<Class<?>> argTypes = List.of(Object.class, Object.class);
        List<Object> argValues = List.of(l, r);
        invokeAssert("assertEquals", argTypes, argValues, argTypes, argValues);
    }

    public static void assertEquals(String msg, long l, long r) {
        List<Class<?>> junit4ArgTypes = List.of(String.class, long.class, long.class);
        List<Object> junit4ArgValues = List.of(msg, l, r);
        List<Class<?>> junit5ArgTypes = List.of(long.class, long.class, String.class);
        List<Object> junit5ArgValues = List.of(l, r, msg);
        invokeAssert("assertEquals", junit4ArgTypes, junit4ArgValues, junit5ArgTypes, junit5ArgValues);
    }

    public static void assertTrue(String msg, boolean b) {
        List<Class<?>> junit4ArgTypes = List.of(String.class, boolean.class);
        List<Object> junit4ArgValues = List.of(msg, b);
        List<Class<?>> junit5ArgTypes = List.of(boolean.class, String.class);
        List<Object> junit5ArgValues = List.of(b, msg);
        invokeAssert("assertTrue", junit4ArgTypes, junit4ArgValues, junit5ArgTypes, junit5ArgValues);
    }

    private static void invokeAssert(String method, List<Class<?>> junit4ArgTypes, List<Object> junit4ArgValues,
                                     List<Class<?>> junit5ArgTypes, List<Object> junit5ArgValues) {
        try {
            invokeAssert("org.junit.jupiter.api.Assertions", method, junit5ArgTypes, junit5ArgValues);
        } catch (ReflectiveOperationException e) {
            try {
                invokeAssert("org.junit.Assert", method, junit4ArgTypes, junit4ArgValues);
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException("Unable to find junit4 or junit5 on test classpath", ex);
            }
        }
    }

    private static void invokeAssert(String clazz, String method, List<Class<?>> argTypes, List<Object> argValues)
            throws ReflectiveOperationException {
        try {
            Class<?> c = Class.forName(clazz);
            Method m = c.getMethod(method, argTypes.toArray(new Class<?>[0]));
            m.invoke(null, argValues.toArray());
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof AssertionError ae) {
                throw ae;
            } else {
                throw new RuntimeException(e.getCause());
            }
        }
    }
}
