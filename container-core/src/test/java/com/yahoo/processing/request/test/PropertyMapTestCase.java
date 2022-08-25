// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request.test;

import com.yahoo.lang.PublicCloneable;
import com.yahoo.processing.request.properties.PropertyMap;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author  bratseth
 */
public class PropertyMapTestCase {

    @Test
    void testObjectCloning() {
        PropertyMap map = new PropertyMap();
        map.set("clonable", new ClonableObject());
        map.set("publicClonable", new PublicClonableObject());
        map.set("nonclonable", new NonClonableObject());
        map.set("clonableArray", new ClonableObject[]{new ClonableObject()});
        map.set("publicClonableArray", new ClonableObject[]{new ClonableObject()});
        map.set("nonclonableArray", new NonClonableObject[]{new NonClonableObject()});
        map.set("clonableList", Collections.singletonList(new ClonableObject()));
        map.set("nonclonableList", Collections.singletonList(new NonClonableObject()));
        assertNotNull(map.get("clonable"));
        assertNotNull(map.get("nonclonable"));

        PropertyMap mapClone = map.clone();
        assertNotSame(map.get("clonable"), mapClone.get("clonable"));
        assertNotSame(map.get("publicClonable"), mapClone.get("publicClonable"));
        assertEquals(map.get("nonclonable"), mapClone.get("nonclonable"));

        assertNotSame(map.get("clonableArray"), mapClone.get("clonableArray"));
        assertNotSame(first(map.get("clonableArray")), first(mapClone.get("clonableArray")));
        assertNotSame(map.get("publicClonableArray"), mapClone.get("publicClonableArray"));
        assertNotSame(first(map.get("publicClonableArray")), first(mapClone.get("publicClonableArray")));
        assertEquals(first(map.get("nonclonableArray")), first(mapClone.get("nonclonableArray")));
    }

    @Test
    void testArrayCloning() {
        PropertyMap map = new PropertyMap();
        byte[] byteArray = new byte[]{2, 4, 7};
        map.set("byteArray", byteArray);

        PropertyMap mapClone = map.clone();
        assertArrayEquals(byteArray, (byte[]) mapClone.get("byteArray"));
        assertNotSame(mapClone.get("byteArray"), byteArray, "Array was cloned");
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

    public static class PublicClonableObject implements PublicCloneable<PublicClonableObject> {

        @Override
        public PublicClonableObject clone() {
            try {
                return (PublicClonableObject)super.clone();
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    private static class NonClonableObject {

    }


}
