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
 * @author : baldersheim
 */
public class CloneHelper {
    private static Logger log = Logger.getLogger(CloneHelper.class.getName());
    private static final MethodCache cloneMethodCache = new MethodCache("clone");
    /**
     * Clones this object if it is clonable, and the clone is public. Returns null if not
     */
    public final Object clone(Object object) {
        if (object == null) return null;
        if (!(object instanceof Cloneable)) return null;
        if (object instanceof Object[])
            return arrayClone((Object[]) object);
        else
            return objectClone(object);
    }

    private final Object arrayClone(Object[] object) {
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
        // Fastpath for our own commonly used classes
        if (object instanceof FreezableClass) {
            // List common superclass of 'com.yahoo.search.result.Hit'
            return ((FreezableClass) object).clone();
        }
        else if (object instanceof PublicCloneable) {
            return ((PublicCloneable)object).clone();
        }
        else if (object instanceof LinkedList) { // TODO: Why? Somebody's infatuation with LinkedList knows no limits
            return ((LinkedList) object).clone();
        }
        else if (object instanceof ArrayList) { // TODO: Why? Likewise
            return ((ArrayList) object).clone();
        }

        try {
            Method cloneMethod = cloneMethodCache.get(object);
            if (cloneMethod == null) {
                log.warning("'" + object + "' is Cloneable, but has no clone method - will use the same instance in all requests");
                return null;
            }
            return cloneMethod.invoke(object);
        } catch (IllegalAccessException e) {
            log.warning("'" + object + "' is Cloneable, but clone method cannot be accessed - will use the same instance in all requests");
            return null;
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Exception cloning '" + object + "'", e);
        }
    }
    /**
     * Clones a map by deep cloning each value which is cloneable and shallow copying all other values.
     */
    public Map<CompoundName, Object> cloneMap(Map<CompoundName, Object> map) {
        Map<CompoundName, Object> cloneMap = new HashMap<>();
        for (Map.Entry<CompoundName, Object> entry : map.entrySet()) {
            Object cloneValue = clone(entry.getValue());
            if (cloneValue == null)
                cloneValue = entry.getValue(); // Shallow copy objects which does not support cloning
            cloneMap.put(entry.getKey(), cloneValue);
        }
        return cloneMap;
    }

}
