// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Einar M R Rosenvinge
 */
public class AnnotationTypeTestCase {

    @Test
    public void testBasic() {
        AnnotationType a = new AnnotationType("foo");
        AnnotationType b = new AnnotationType("foo");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.hashCode(), a.getId());
        assertEquals(b.hashCode(), b.getId());

        AnnotationType c = new AnnotationType("bar");
        assertEquals(c.hashCode(), c.getId());

        assertFalse(a.equals(c));
        assertFalse(c.equals(a));
        assertFalse(b.equals(c));
        assertFalse(c.equals(b));

        assertFalse(a.hashCode() == c.hashCode());
        assertFalse(c.hashCode() == a.hashCode());
        assertFalse(b.hashCode() == c.hashCode());
        assertFalse(c.hashCode() == b.hashCode());
    }

    @Test
    public void testBasic2() {
        AnnotationType a = new AnnotationType("foo", DataType.INT);
        AnnotationType b = new AnnotationType("foo", DataType.INT);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.hashCode(), a.getId());
        assertEquals(b.hashCode(), b.getId());

        AnnotationType c = new AnnotationType("foo", DataType.FLOAT);
        assertEquals(c.hashCode(), c.getId());

        assertEquals(a, c);
        assertEquals(a.hashCode(), c.hashCode());
        assertEquals(a.hashCode(), a.getId());
        assertEquals(c.hashCode(), c.getId());
    }

    @Test
    public void testPolymorphy() {
        AnnotationType suuper = new AnnotationType("super");
        AnnotationType sub = new AnnotationType("sub");
        sub.inherit(suuper);

        //reference type for super annotation type
        AnnotationReferenceDataType refType = new AnnotationReferenceDataType(suuper);

        Annotation superAnnotation = new Annotation(suuper);
        Annotation subAnnotation = new Annotation(sub);

        AnnotationReference ref1 = new AnnotationReference(refType, superAnnotation);
        //this would fail without polymorphy support:
        AnnotationReference ref2 = new AnnotationReference(refType, subAnnotation);
    }

}
