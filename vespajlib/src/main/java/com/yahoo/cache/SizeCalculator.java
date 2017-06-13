// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.cache;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Size calculator for objects.
 * Thread safe.
 * @author vegardh
 * @see <a href="http://www.javaspecialists.co.za/archive/Issue078.html">MemoryCounter by Dr H M Kabutz</a>
 */
public class SizeCalculator {

    private static class ObjectSet {
        private final Map<Object, Object> map = new IdentityHashMap<>();

        public boolean had(Object obj) {
            if (map.containsKey(obj)) {
                return true;
            }
            map.put(obj, null);
            return false;
        }
    }

    private int getPointerSize() {
        return 4;
    }

    private int getClassSize() {
        return 8;
    }

    private int getArraySize() {
        return 16;
    }

    @SuppressWarnings("serial")
    private final IdentityHashMap<Class<?>, Integer> primitiveSizes = new IdentityHashMap<Class<?>, Integer>() {
        {
            put(boolean.class, 1);
            put(byte.class, 1);
            put(char.class, 2);
            put(short.class, 2);
            put(int.class, 4);
            put(float.class, 4);
            put(double.class, 8);
            put(long.class, 8);
        }
    };

    // Only called on un-visited objects and only with array.
    private long sizeOfArray(Object a, ObjectSet visitedObjects) {
        long sum = getArraySize();
        int length = Array.getLength(a);
        if (length == 0) {
            return sum;
        }
        Class<?> elementClass = a.getClass().getComponentType();
        if (elementClass.isPrimitive()) {
            sum += length * (primitiveSizes.get(elementClass));
            return sum;
        } else {
            for (int i = 0; i < length; i++) {
                Object val = Array.get(a, i);
                sum += getPointerSize();
                sum += sizeOfObject(val, visitedObjects);
            }
            return sum;
        }
    }

    private long getSumOfFields(Class<?> clas, Object obj,
            ObjectSet visitedObjects) {
        long sum = 0;
        Field[] fields = clas.getDeclaredFields();
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                if (field.getType().isPrimitive()) {
                    sum += primitiveSizes.get(field.getType());
                } else {
                    sum += getPointerSize();
                    field.setAccessible(true);
                    try {
                        sum += sizeOfObject(field.get(obj), visitedObjects);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return sum;
    }

    // Skip literal strings
    private boolean isIntern(Object obj) {
        if (obj instanceof String) {
            if (obj == ((String) obj).intern()) {
                return true;
            }
        }
        return false;
    }

    // Only called on non-visited non-arrays.
    private long sizeOfNonArray(Class<?> clas, Object obj,
            ObjectSet visitedObjects) {
        if (isIntern(obj)) {
            return 0;
        }
        long sum = getClassSize();
        while (clas != null) {
            sum += getSumOfFields(clas, obj, visitedObjects);
            clas = clas.getSuperclass();
        }
        return sum;
    }

    private long sizeOfObject(Object obj, ObjectSet visitedObjects) {
        if (obj == null) {
            return 0;
        }
        if (visitedObjects.had(obj)) {
            return 0;
        }
        Class<?> clas = obj.getClass();
        if (clas.isArray()) {
            return sizeOfArray(obj, visitedObjects);
        }
        return sizeOfNonArray(clas, obj, visitedObjects);
    }

    /**
     * Returns the heap size of an object/array
     *
     * @return Number of bytes for object, approximately
     */
    public long sizeOf(Object value) {
        ObjectSet visitedObjects = new ObjectSet();
        return sizeOfObject(value, visitedObjects);
    }

    /**
     * Returns the heap size of two objects/arrays, common objects counted only
     * once
     *
     * @return Number of bytes for objects, approximately
     */
    public long sizeOf(Object value1, Object value2) {
        ObjectSet visitedObjects = new ObjectSet();
        return sizeOfObject(value1, visitedObjects)
                + sizeOfObject(value2, visitedObjects);
    }

    /**
     * The approximate size in bytes for a list of objects, viewed as a closure,
     * ie. common objects are counted only once.
     *
     * @return total number of bytes
     */
    public long sizeOf(List<?> objects) {
        ObjectSet visitedObjects = new ObjectSet();
        long sum = 0;
        for (Object o : objects) {
            sum += sizeOfObject(o, visitedObjects);
        }
        return sum;
    }

}
