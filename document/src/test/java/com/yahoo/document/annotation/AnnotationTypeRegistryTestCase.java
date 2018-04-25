// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Einar M R Rosenvinge
 */
public class AnnotationTypeRegistryTestCase {

    @Test
    public void testRegisterUnregister() {
        AnnotationTypeRegistry reg = new AnnotationTypeRegistry();
        assertEquals(0, reg.getTypes().size());

        AnnotationType one = new AnnotationType("one");
        AnnotationType another = new AnnotationType("one");

        //should work; re-registering type with same name and same id:
        reg.register(one);
        assertEquals(1, reg.getTypes().size());
        reg.register(another);
        assertEquals(1, reg.getTypes().size());

        AnnotationType oneWithData = new AnnotationType("one", DataType.INT);

        reg.register(oneWithData);
        assertEquals(1, reg.getTypes().size());


        AnnotationType two = new AnnotationType("two");
        AnnotationType three = new AnnotationType("three");

        reg.register(two);
        assertEquals(2, reg.getTypes().size());
        reg.register(three);
        assertEquals(3, reg.getTypes().size());


        reg.unregister("one");
        assertEquals(2, reg.getTypes().size());
        assertEquals("two", reg.getType("two").getName());
        assertEquals("three", reg.getType("three").getName());

        reg.unregister(two.getId());
        assertEquals(1, reg.getTypes().size());
        assertEquals("three", reg.getType("three").getName());

        reg.unregister(three);
        assertEquals(0, reg.getTypes().size());
    }

}
