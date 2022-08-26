// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties.test;

import com.yahoo.processing.request.properties.PropertyMap;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class PropertyMapTestCase {

    @Test
    void testCloning() {
        PropertyMap map = new PropertyMap();
        map.set("clonable", new ClonableObject());
        map.set("nonclonable", new NonClonableObject());
        map.set("clonableArray", new ClonableObject[]{new ClonableObject()});
        map.set("nonclonableArray", new NonClonableObject[]{new NonClonableObject()});
        map.set("clonableList", Collections.singletonList(new ClonableObject()));
        map.set("nonclonableList", Collections.singletonList(new NonClonableObject()));
        assertNotNull(map.get("clonable"));
        assertNotNull(map.get("nonclonable"));

        PropertyMap mapClone = map.clone();
        assertNotSame(map.get("clonable"), mapClone.get("clonable"));
        assertEquals(map.get("nonclonable"), mapClone.get("nonclonable"));

        assertNotSame(map.get("clonableArray"), mapClone.get("clonableArray"));
        assertNotSame(first(map.get("clonableArray")), first(mapClone.get("clonableArray")));
        assertEquals(first(map.get("nonclonableArray")), first(mapClone.get("nonclonableArray")));
    }

    private Object first(Object object) {
        if (object instanceof Object[])
            return ((Object[])object)[0];
        if (object instanceof List)
            return ((List<?>)object).get(0);
        throw new IllegalArgumentException();
    }

    public static class ClonableObject implements Cloneable {

        @Override
        public ClonableObject clone() {
            try {
                return (ClonableObject)super.clone();
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    private static class NonClonableObject {

    }

}
