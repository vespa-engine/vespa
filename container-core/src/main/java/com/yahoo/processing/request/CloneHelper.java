// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request;

import com.yahoo.collections.MethodCache;
import com.yahoo.component.provider.FreezableClass;
import com.yahoo.processing.request.properties.PublicCloneable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

/**
 * Helps to deep clone complex objects
 * The following classes and their subclasses does have a fastpath
 * - com.yahoo.component.provider.FreezableClass
 *  - com.yahoo.processing.request.properties.PublicCloneable BTW, this is the one you should implement too
 *    if you want the fastpath.
 *  - java.util.LinkedList
 *  - java.util.ArrayList
 * The rest has the slow path with reflection,
 * though using a fast thread safe method cache for speedup.
 *
 * @author bratseth
 * @author baldersheim
 */
public class CloneHelper {

    private static Logger log = Logger.getLogger(CloneHelper.class.getName());
    private static final MethodCache cloneMethodCache = new MethodCache("clone");

    /**
     * Clones this object if it is clonable, and the clone is public. Returns null if not
     */
    public final Object clone(Object object) {
        if (object == null) return null;
        if ( ! (object instanceof Cloneable)) return null;
        if (object.getClass().isArray())
            return arrayClone(object);
        else
            return objectClone(object);
    }

    private Object arrayClone(Object array) {
        if (array instanceof Object[])
            return objectArrayClone((Object[]) array);
        else if (array instanceof byte[])
            return Arrays.copyOf((byte[])array, ((byte[])array).length);
        else if (array instanceof char[])
            return Arrays.copyOf((char[])array, ((char[])array).length);
        else if (array instanceof short[])
            return Arrays.copyOf((short[])array, ((short[])array).length);
        else if (array instanceof int[])
            return Arrays.copyOf((int[])array, ((int[])array).length);
        else if (array instanceof long[])
            return Arrays.copyOf((long[])array, ((long[])array).length);
        else if (array instanceof float[])
            return Arrays.copyOf((float[])array, ((float[])array).length);
        else if (array instanceof double[])
            return Arrays.copyOf((double[])array, ((double[])array).length);
        else if (array instanceof boolean[])
            return Arrays.copyOf((boolean[])array, ((boolean[])array).length);
        else
            return new IllegalArgumentException("Unexpected primitive array type " + array.getClass());
    }
    
    private Object objectArrayClone(Object[] object) {
        Object[] arrayClone = Arrays.copyOf(object, object.length);
        // deep clone
        for (int i = 0; i < arrayClone.length; i++) {
            Object elementClone = clone(arrayClone[i]);
            if (elementClone != null)
                arrayClone[i] = elementClone;
        }
        return arrayClone;
    }

    protected Object objectClone(Object object) {
        // Fastpath for our commonly used classes
        if (object instanceof FreezableClass)
            return ((FreezableClass)object).clone();
        else if (object instanceof PublicCloneable)
            return ((PublicCloneable<?>)object).clone();
        else if (object instanceof LinkedList)
            return ((LinkedList<?>) object).clone();
        else if (object instanceof ArrayList)
            return ((ArrayList<?>) object).clone();

        try {
            Method cloneMethod = cloneMethodCache.get(object);
            if (cloneMethod == null) {
                log.warning("'" + object + "' of class " + object.getClass() + 
                            " is Cloneable, but has no clone method - will use the same instance in all requests");
                return null;
            }
            return cloneMethod.invoke(object);
        } catch (IllegalAccessException e) {
            log.warning("'" + object + "' of class " + object.getClass() + 
                        " is Cloneable, but clone method cannot be accessed - will use the same instance in all requests");
            return null;
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Exception cloning '" + object + "'", e);
        }
    }

    /**
     * Clones a map by deep cloning each value which is cloneable and shallow copying all other values.
     */
    public Map<CompoundName, Object> cloneMap(Map<CompoundName, Object> map) {
        Map<CompoundName, Object> cloneMap = new HashMap<>(map.size());
        for (Map.Entry<CompoundName, Object> entry : map.entrySet()) {
            Object cloneValue = clone(entry.getValue());
            if (cloneValue == null)
                cloneValue = entry.getValue(); // Shallow copy objects which does not support cloning
            cloneMap.put(entry.getKey(), cloneValue);
        }
        return cloneMap;
    }

}
