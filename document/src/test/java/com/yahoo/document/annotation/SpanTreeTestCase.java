// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class SpanTreeTestCase extends AbstractTypesTest {
    SpanTree tree;
    SpanList root;
    SpanNode a;
    SpanNode b;
    SpanList c;
    SpanNode d;
    SpanNode e;
    Annotation dummyA;
    Annotation numberB;
    Annotation bananaC;
    Annotation appleC;
    Annotation grapeC;
    Annotation dummyD;
    Annotation dummyE;

    /**
     * Sets up a simple tree:
     * root:
     * a (dummyA), b (numberB), c (bananaC, appleC, grapeC)
     * c:
     * d (dummyD), e (dummyE)
     *
     */
    @Before
    public void setUp() {
        tree = new SpanTree("ballooo");
        root = (SpanList) tree.getRoot();

        a = new Span(1,1);
        b = new Span(2,1);
        c = new SpanList();
        d = new Span(3,1);
        e = new Span(4,1);

        c.add(d).add(e);
        root.add(a).add(b).add(c);

        dummyA = new Annotation(dummy);
        numberB = new Annotation(number);
        bananaC = new Annotation(banana);
        appleC = new Annotation(apple);
        grapeC = new Annotation(grape);
        dummyD = new Annotation(dummy);
        dummyE = new Annotation(dummy);

        tree.annotate(a, dummyA)
                .annotate(b, numberB)
                .annotate(c, bananaC)
                .annotate(c, appleC)
                .annotate(c, grapeC)
                .annotate(d, dummyD)
                .annotate(e, dummyE);
    }

    /**
     * Tests iterating through all annotations in a tree.
     */
    @Test
    public void testAnnotationIteration() {
        List<Annotation> allAnnotationsList = new ArrayList<>();
        for (Annotation a : tree) {
            allAnnotationsList.add(a);
        }
        Collections.sort(allAnnotationsList);

        Iterator<Annotation> allAnnotations = allAnnotationsList.iterator();

        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());

        assertSame(dummyA, allAnnotations.next());
        assertSame(a, dummyA.getSpanNode());

        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());

        assertSame(numberB, allAnnotations.next());
        assertSame(b, numberB.getSpanNode());

        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());

        assertSame(dummyD, allAnnotations.next());
        assertSame(d, dummyD.getSpanNode());

        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());

        assertSame(grapeC, allAnnotations.next());
        assertSame(c, grapeC.getSpanNode());

        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());

        assertSame(bananaC, allAnnotations.next());
        assertSame(c, bananaC.getSpanNode());

        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());

        assertSame(appleC, allAnnotations.next());
        assertSame(c, appleC.getSpanNode());

        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());
        assertTrue(allAnnotations.hasNext());


        assertSame(dummyE, allAnnotations.next());
        assertSame(e, dummyE.getSpanNode());

        assertFalse(allAnnotations.hasNext());
        assertFalse(allAnnotations.hasNext());
        assertFalse(allAnnotations.hasNext());
        assertFalse(allAnnotations.hasNext());
    }

    /**
     * Tests iterating through A's annotations in a tree.
     */
    @Test
    public void testAnnotationIterationForA() {
        //A has no child nodes, should have equal results for recursive and non-recursive iterator
        aIteration(tree.iterator(a));
        aIteration(tree.iteratorRecursive(a));
    }

    private void aIteration(Iterator<Annotation> aAnnotations) {
        assertTrue(aAnnotations.hasNext());
        assertTrue(aAnnotations.hasNext());
        assertTrue(aAnnotations.hasNext());
        assertTrue(aAnnotations.hasNext());

        assertSame(dummyA, aAnnotations.next());
        assertSame(a, dummyA.getSpanNode());

        assertFalse(aAnnotations.hasNext());
        assertFalse(aAnnotations.hasNext());
        assertFalse(aAnnotations.hasNext());
        assertFalse(aAnnotations.hasNext());
    }

    /**
     * Tests iterating through B's annotations in a tree.
     */
    @Test
    public void testAnnotationIterationForB() {
        //B has no child nodes, should have equal results for recursive and non-recursive iterator
        bIteration(tree.iterator(b));
        bIteration(tree.iteratorRecursive(b));
    }

    private void bIteration(Iterator<Annotation> bAnnotations) {
        assertTrue(bAnnotations.hasNext());
        assertTrue(bAnnotations.hasNext());
        assertTrue(bAnnotations.hasNext());
        assertTrue(bAnnotations.hasNext());

        assertSame(numberB, bAnnotations.next());
        assertSame(b, numberB.getSpanNode());

        assertFalse(bAnnotations.hasNext());
        assertFalse(bAnnotations.hasNext());
        assertFalse(bAnnotations.hasNext());
        assertFalse(bAnnotations.hasNext());
    }

    /**
     * Tests iterating through C's annotations in a tree.
     */
    @Test
    public void testAnnotationIterationForC() {
        List<Annotation> cAnnotationsList = new ArrayList<>();
        for (Iterator<Annotation> iteratorc = tree.iterator(c); iteratorc.hasNext(); ) {
            cAnnotationsList.add(iteratorc.next());
        }
        Collections.sort(cAnnotationsList);

        Iterator<Annotation> cAnnotations = cAnnotationsList.iterator();

        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());

        assertSame(grapeC, cAnnotations.next());
        assertSame(c, grapeC.getSpanNode());

        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());

        assertSame(bananaC, cAnnotations.next());
        assertSame(c, bananaC.getSpanNode());

        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());

        assertSame(appleC, cAnnotations.next());
        assertSame(c, appleC.getSpanNode());

        assertFalse(cAnnotations.hasNext());
        assertFalse(cAnnotations.hasNext());
        assertFalse(cAnnotations.hasNext());
        assertFalse(cAnnotations.hasNext());
    }

    @Test
    public void testRecursiveAnnotationIterationForC() {
        List<Annotation> cAnnotationsList = new ArrayList<>();
        for (Iterator<Annotation> iteratorc = tree.iteratorRecursive(c); iteratorc.hasNext(); ) {
            cAnnotationsList.add(iteratorc.next());
        }
        Collections.sort(cAnnotationsList);

        Iterator<Annotation> cAnnotations = cAnnotationsList.iterator();

        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());

        assertSame(dummyD, cAnnotations.next());
        assertSame(d, dummyD.getSpanNode());

        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());

        assertSame(grapeC, cAnnotations.next());
        assertSame(c, grapeC.getSpanNode());

        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());

        assertSame(bananaC, cAnnotations.next());
        assertSame(c, bananaC.getSpanNode());

        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());

        assertSame(appleC, cAnnotations.next());
        assertSame(c, appleC.getSpanNode());

        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());
        assertTrue(cAnnotations.hasNext());

        assertSame(dummyE, cAnnotations.next());
        assertSame(e, dummyE.getSpanNode());

        assertFalse(cAnnotations.hasNext());
        assertFalse(cAnnotations.hasNext());
        assertFalse(cAnnotations.hasNext());
        assertFalse(cAnnotations.hasNext());
    }


    /**
     * Tests iterating through D's annotations in a tree.
     */
    @Test
    public void testAnnotationIterationForD() {
        //D has no child nodes, should have equal results for recursive and non-recursive iterator
        dIteration(tree.iterator(d));
        dIteration(tree.iteratorRecursive(d));
    }

    private void dIteration(Iterator<Annotation> dAnnotations) {
        assertTrue(dAnnotations.hasNext());
        assertTrue(dAnnotations.hasNext());
        assertTrue(dAnnotations.hasNext());
        assertTrue(dAnnotations.hasNext());

        assertSame(dummyD, dAnnotations.next());
        assertSame(d, dummyD.getSpanNode());

        assertFalse(dAnnotations.hasNext());
        assertFalse(dAnnotations.hasNext());
        assertFalse(dAnnotations.hasNext());
        assertFalse(dAnnotations.hasNext());
    }


    /**
     * Tests iterating through E's annotations in a tree.
     */
    @Test
    public void testAnnotationIterationForE() {
        //E has no child nodes, should have equal results for recursive and non-recursive iterator
        eIteration(tree.iterator(e));
        eIteration(tree.iteratorRecursive(e));
    }

    private void eIteration(Iterator<Annotation> eAnnotations) {
        assertTrue(eAnnotations.hasNext());
        assertTrue(eAnnotations.hasNext());
        assertTrue(eAnnotations.hasNext());
        assertTrue(eAnnotations.hasNext());

        assertSame(dummyE, eAnnotations.next());
        assertSame(e, dummyE.getSpanNode());

        assertFalse(eAnnotations.hasNext());
        assertFalse(eAnnotations.hasNext());
        assertFalse(eAnnotations.hasNext());
        assertFalse(eAnnotations.hasNext());
    }

    @Test
    public void testCleanup() {
        SpanList a = new SpanList();
        SpanTree tree = new SpanTree("ballooO", a);
        Span b = new Span(0,1);
        Span c = new Span(1,1);
        a.add(b).add(c);

        AnnotationReferenceDataType refTypeToDummy = new AnnotationReferenceDataType(dummy);
        AnnotationType annotationTypeWithRefToDummy = new AnnotationType("reftodummy", refTypeToDummy);

        AnnotationReferenceDataType refTypeToRefTypeToDummy = new AnnotationReferenceDataType(annotationTypeWithRefToDummy);
        AnnotationType annotationTypeWithRefToRefToDummy = new AnnotationType("reftoreftodummy", refTypeToRefTypeToDummy);

        StructDataType structType = new StructDataType("str");
        Field refToDummyField = new Field("refToDummy", refTypeToDummy);
        structType.addField(refToDummyField);
        AnnotationType annotationTypeWithStruct = new AnnotationType("structwithref", structType);


        Annotation dummyAnnotationForB = new Annotation(dummy);
        tree.annotate(b, dummyAnnotationForB);

        AnnotationReference referenceToDummyB = new AnnotationReference(refTypeToDummy, dummyAnnotationForB);
        Annotation annotationWithRefToDummyB = new Annotation(annotationTypeWithRefToDummy, referenceToDummyB);
        tree.annotate(annotationWithRefToDummyB);

        AnnotationReference referenceToRefToDummyB = new AnnotationReference(refTypeToRefTypeToDummy, annotationWithRefToDummyB);
        Annotation annotationWithRefToRefToDummyB = new Annotation(annotationTypeWithRefToRefToDummy, referenceToRefToDummyB);
        tree.annotate(annotationWithRefToRefToDummyB);

        Struct struct = new Struct(structType);
        struct.setFieldValue(refToDummyField, new AnnotationReference(refTypeToDummy, dummyAnnotationForB));
        Annotation annotationWithStruct = new Annotation(annotationTypeWithStruct, struct);
        tree.annotate(annotationWithStruct);


        Iterator<Annotation> annotationIt;

        assertEquals(4, tree.numAnnotations());
        assertTrue(a.isValid());
        assertTrue(b.isValid());
        assertTrue(c.isValid());
        assertEquals(2, a.numChildren());

        {
            List<Annotation> allAnnotationsList = new ArrayList<>();
            for (Annotation an : tree) {
                allAnnotationsList.add(an);
            }
            Collections.sort(allAnnotationsList);

            annotationIt = allAnnotationsList.iterator();
        }
        // NOTE: ordering of annotations is derived from their name

        assertSame(annotationWithRefToDummyB, annotationIt.next());
        assertFalse(annotationWithRefToDummyB.hasSpanNode());
        assertFalse(annotationWithRefToDummyB.isSpanNodeValid());
        assertTrue(annotationWithRefToDummyB.hasFieldValue());
        assertSame(dummyAnnotationForB, ((AnnotationReference) annotationWithRefToDummyB.getFieldValue()).getReference());

        assertSame(annotationWithStruct, annotationIt.next());
        assertFalse(annotationWithStruct.hasSpanNode());
        assertFalse(annotationWithStruct.isSpanNodeValid());
        assertTrue(annotationWithStruct.hasFieldValue());
        assertSame(struct, annotationWithStruct.getFieldValue());
        assertSame(dummyAnnotationForB, ((AnnotationReference) struct.getFieldValue(refToDummyField)).getReference());

        assertSame(annotationWithRefToRefToDummyB, annotationIt.next());
        assertFalse(annotationWithRefToRefToDummyB.hasSpanNode());
        assertFalse(annotationWithRefToRefToDummyB.isSpanNodeValid());
        assertTrue(annotationWithRefToRefToDummyB.hasFieldValue());
        assertSame(annotationWithRefToDummyB, ((AnnotationReference) annotationWithRefToRefToDummyB.getFieldValue()).getReference());

        assertSame(dummyAnnotationForB, annotationIt.next());
        assertTrue(dummyAnnotationForB.hasSpanNode());
        assertTrue(dummyAnnotationForB.isSpanNodeValid());
        assertFalse(dummyAnnotationForB.hasFieldValue());

        assertFalse(annotationIt.hasNext());

        //removing b!!
        a.remove(b);


        assertEquals(4, tree.numAnnotations());
        assertTrue(a.isValid());
        assertFalse(b.isValid());
        assertTrue(c.isValid());
        assertEquals(1, a.numChildren());

        {
            List<Annotation> allAnnotationsList = new ArrayList<>();
            for (Annotation an : tree) {
                allAnnotationsList.add(an);
            }
            Collections.sort(allAnnotationsList);

            annotationIt = allAnnotationsList.iterator();
        }

        assertSame(annotationWithRefToDummyB, annotationIt.next());
        assertFalse(annotationWithRefToDummyB.hasSpanNode());
        assertFalse(annotationWithRefToDummyB.isSpanNodeValid());
        assertTrue(annotationWithRefToDummyB.hasFieldValue());
        assertSame(dummyAnnotationForB, ((AnnotationReference) annotationWithRefToDummyB.getFieldValue()).getReference());

        assertSame(annotationWithStruct, annotationIt.next());
        assertFalse(annotationWithStruct.hasSpanNode());
        assertFalse(annotationWithStruct.isSpanNodeValid());
        assertTrue(annotationWithStruct.hasFieldValue());
        assertSame(struct, annotationWithStruct.getFieldValue());
        assertSame(dummyAnnotationForB, ((AnnotationReference) struct.getFieldValue(refToDummyField)).getReference());

        assertSame(annotationWithRefToRefToDummyB, annotationIt.next());
        assertFalse(annotationWithRefToRefToDummyB.hasSpanNode());
        assertFalse(annotationWithRefToRefToDummyB.isSpanNodeValid());
        assertTrue(annotationWithRefToRefToDummyB.hasFieldValue());
        assertSame(annotationWithRefToDummyB, ((AnnotationReference) annotationWithRefToRefToDummyB.getFieldValue()).getReference());

        assertSame(dummyAnnotationForB, annotationIt.next());
        assertTrue(dummyAnnotationForB.hasSpanNode());
        assertFalse(dummyAnnotationForB.isSpanNodeValid());
        assertFalse(dummyAnnotationForB.hasFieldValue());

        assertFalse(annotationIt.hasNext());


        //cleaning up tree!!
        tree.cleanup();

        assertEquals(1, tree.numAnnotations());
        assertTrue(a.isValid());
        assertFalse(b.isValid());
        assertTrue(c.isValid());
        assertEquals(1, a.numChildren());

        assertFalse(dummyAnnotationForB.hasSpanNode());
        assertFalse(dummyAnnotationForB.isSpanNodeValid());
        assertFalse(dummyAnnotationForB.hasFieldValue());

        assertFalse(annotationWithRefToDummyB.hasSpanNode());
        assertFalse(annotationWithRefToDummyB.isSpanNodeValid());
        assertFalse(annotationWithRefToDummyB.hasFieldValue());

        assertFalse(annotationWithRefToRefToDummyB.hasSpanNode());
        assertFalse(annotationWithRefToRefToDummyB.isSpanNodeValid());
        assertFalse(annotationWithRefToRefToDummyB.hasFieldValue());

        annotationIt = tree.iterator();
        assertSame(annotationWithStruct, annotationIt.next());
        assertFalse(annotationWithStruct.hasSpanNode());
        assertFalse(annotationWithStruct.isSpanNodeValid());
        assertTrue(annotationWithStruct.hasFieldValue());
        assertSame(struct, annotationWithStruct.getFieldValue());
        assertNull(struct.getFieldValue(refToDummyField));

        assertFalse(annotationIt.hasNext());
    }

    @Test
    public void testSimpleCopy() {
        StringFieldValue string = new StringFieldValue("yahoooo");

        SpanList list = new SpanList();
        Span span1 = new Span(0,1);
        Span span2 = new Span(1,1);
        list.add(span1).add(span2);
        SpanTree tree = new SpanTree("foo", list);
        string.setSpanTree(tree);

        Annotation a1 = new Annotation(grape);
        tree.annotate(span1, a1);
        Annotation a2 = new Annotation(apple);
        tree.annotate(span2, a2);

        StringFieldValue stringCopy = string.clone();



        assertEquals(string, stringCopy);
        assertNotSame(string, stringCopy);

        SpanTree treeCopy = stringCopy.getSpanTree("foo");
        assertEquals(tree, treeCopy);
        assertNotSame(tree, treeCopy);

        SpanList listCopy = (SpanList) treeCopy.getRoot();
        assertEquals(list, listCopy);
        assertNotSame(list, listCopy);

        Span span1Copy = (Span) listCopy.children().get(0);
        assertEquals(span1, span1Copy);
        assertNotSame(span1, span1Copy);

        Span span2Copy = (Span) listCopy.children().get(1);
        assertEquals(span2, span2Copy);
        assertNotSame(span2, span2Copy);

        Iterator<Annotation> annotationIt;
        {
            List<Annotation> allAnnotationsList = new ArrayList<>();
            for (Annotation an : treeCopy) {
                allAnnotationsList.add(an);
            }
            Collections.sort(allAnnotationsList);

            annotationIt = allAnnotationsList.iterator();
        }

        Annotation a1Copy = annotationIt.next();
        assertEquals(a1, a1Copy);
        assertNotSame(a1, a1Copy);
        assertSame(span1Copy, a1Copy.getSpanNode());
        assertNotSame(span1, a1Copy.getSpanNode());

        Annotation a2Copy = annotationIt.next();
        assertEquals(a2, a2Copy);
        assertNotSame(a2, a2Copy);
        assertSame(span2Copy, a2Copy.getSpanNode());
        assertNotSame(span2, a2Copy.getSpanNode());
    }

    @Test
    public void testSimpleCopy2() {
        StringFieldValue string = new StringFieldValue("yahoooo");

        SpanList list = new SpanList();
        Span span1 = new Span(0,1);
        Span span2 = new Span(1,1);
        list.add(span1).add(span2);
        SpanTree tree = new SpanTree("foo", list);
        string.setSpanTree(tree);

        Annotation a1 = new Annotation(grape);
        tree.annotate(span1, a1);
        Annotation a2 = new Annotation(apple);
        tree.annotate(span2, a2);

        Struct donald = new Struct(person);
        donald.setFieldValue("firstname", "donald");
        donald.setFieldValue("lastname", "duck");
        donald.setFieldValue("birthyear", 1929);
        Annotation donaldAnn = new Annotation(personA, donald);
        tree.annotate(list, donaldAnn);


        StringFieldValue stringCopy = string.clone();



        assertEquals(string, stringCopy);
        assertNotSame(string, stringCopy);

        SpanTree treeCopy = stringCopy.getSpanTree("foo");
        assertEquals(tree, treeCopy);
        assertNotSame(tree, treeCopy);

        SpanList listCopy = (SpanList) treeCopy.getRoot();
        assertEquals(list, listCopy);
        assertNotSame(list, listCopy);

        Span span1Copy = (Span) listCopy.children().get(0);
        assertEquals(span1, span1Copy);
        assertNotSame(span1, span1Copy);

        Span span2Copy = (Span) listCopy.children().get(1);
        assertEquals(span2, span2Copy);
        assertNotSame(span2, span2Copy);

        Iterator<Annotation> annotationIt;
        {
            List<Annotation> allAnnotationsList = new ArrayList<>();
            for (Annotation an : treeCopy) {
                allAnnotationsList.add(an);
            }
            Collections.sort(allAnnotationsList);

            annotationIt = allAnnotationsList.iterator();
        }

        Annotation a1Copy = annotationIt.next();
        assertEquals(a1, a1Copy);
        assertNotSame(a1, a1Copy);
        assertSame(span1Copy, a1Copy.getSpanNode());
        assertNotSame(span1, a1Copy.getSpanNode());

        Annotation donaldAnnCopy = annotationIt.next();
        assertEquals(donaldAnn, donaldAnnCopy);
        assertNotSame(donaldAnn, donaldAnnCopy);
        assertSame(listCopy, donaldAnnCopy.getSpanNode());
        assertNotSame(list, donaldAnnCopy.getSpanNode());

        Struct donaldCopy = (Struct) donaldAnnCopy.getFieldValue();
        assertEquals(donald, donaldCopy);
        assertNotSame(donald, donaldCopy);

        Annotation a2Copy = annotationIt.next();
        assertEquals(a2, a2Copy);
        assertNotSame(a2, a2Copy);
        assertSame(span2Copy, a2Copy.getSpanNode());
        assertNotSame(span2, a2Copy.getSpanNode());
    }

    @Test
    public void testCopyAnnotatedString() {
        StringFieldValue str = getAnnotatedString();
        StringFieldValue strCopy = str.clone();

        SpanTree balloooTree = str.getSpanTree("ballooo");
        AlternateSpanList root = (AlternateSpanList) balloooTree.getRoot();
        Span s1 = (Span) root.children(0).get(0);
        Span s2 = (Span) root.children(0).get(1);
        Span s3 = (Span) root.children(0).get(2);
        AlternateSpanList s4 = (AlternateSpanList) root.children(0).get(3);
        Span s5 = (Span) s4.children(0).get(0);
        Span s6 = (Span) s4.children(0).get(1);
        Span s7 = (Span) root.children(1).get(0);
        Span s8 = (Span) root.children(1).get(1);
        Span s9 = (Span) root.children(1).get(2);

        SpanTree balloooTreeCopy = strCopy.getSpanTree("ballooo");
        assertEquals(balloooTree, balloooTreeCopy);
        assertNotSame(balloooTree, balloooTreeCopy);
        AlternateSpanList rootCopy = (AlternateSpanList) balloooTreeCopy.getRoot();
        assertEquals(root, rootCopy);
        assertNotSame(root, rootCopy);
        Span s1Copy = (Span) rootCopy.children(0).get(0);
        assertEquals(s1, s1Copy);
        assertNotSame(s1, s1Copy);
        Span s2Copy = (Span) rootCopy.children(0).get(1);
        assertEquals(s2, s2Copy);
        assertNotSame(s2, s2Copy);
        Span s3Copy = (Span) rootCopy.children(0).get(2);
        assertEquals(s3, s3Copy);
        assertNotSame(s3, s3Copy);
        AlternateSpanList s4Copy = (AlternateSpanList) rootCopy.children(0).get(3);
        assertEquals(s4, s4Copy);
        assertNotSame(s4, s4Copy);
        Span s5Copy = (Span) s4Copy.children(0).get(0);
        assertEquals(s5, s5Copy);
        assertNotSame(s5, s5Copy);
        Span s6Copy = (Span) s4Copy.children(0).get(1);
        assertEquals(s6, s6Copy);
        assertNotSame(s6, s6Copy);
        Span s7Copy = (Span) rootCopy.children(1).get(0);
        assertEquals(s7, s7Copy);
        assertNotSame(s7, s7Copy);
        Span s8Copy = (Span) rootCopy.children(1).get(1);
        assertEquals(s8, s8Copy);
        assertNotSame(s8, s8Copy);
        Span s9Copy = (Span) rootCopy.children(1).get(2);
        assertEquals(s9, s9Copy);
        assertNotSame(s9, s9Copy);


        Iterator<Annotation> annotationsBalloooTree;
        {
            List<Annotation> allAnnotationsList = new ArrayList<>();
            for (Annotation an : balloooTree) {
                allAnnotationsList.add(an);
            }
            Collections.sort(allAnnotationsList);

            annotationsBalloooTree = allAnnotationsList.iterator();
        }

        Annotation dummyAnnForS1 = annotationsBalloooTree.next();

        Annotation dummyAnnForS2 = annotationsBalloooTree.next();

        Annotation numberAnnForS2 = annotationsBalloooTree.next();
        IntegerFieldValue integerValForS2 = (IntegerFieldValue) numberAnnForS2.getFieldValue();

        Annotation motherAnnForS2 = annotationsBalloooTree.next();
        Struct motherValForS2 = (Struct) motherAnnForS2.getFieldValue();

        Annotation dummyAnnForS3 = annotationsBalloooTree.next();

        Annotation numberAnnForS3 = annotationsBalloooTree.next();
        IntegerFieldValue integerValForS3 = (IntegerFieldValue) numberAnnForS3.getFieldValue();

        Annotation dummyAnnForS5 = annotationsBalloooTree.next();

        Annotation daughterAnnForS6 = annotationsBalloooTree.next();
        Struct daughterValForS6 = (Struct) daughterAnnForS6.getFieldValue();
        AnnotationReference refFromS6ToMotherAnn = (AnnotationReference) daughterValForS6.getFieldValue("related");


        Iterator<Annotation> annotationsBalloooTreeCopy;
        {
            List<Annotation> allAnnotationsList = new ArrayList<>();
            for (Annotation an : balloooTreeCopy) {
                allAnnotationsList.add(an);
            }
            Collections.sort(allAnnotationsList);

            annotationsBalloooTreeCopy = allAnnotationsList.iterator();
        }

        Annotation dummyAnnForS1Copy = annotationsBalloooTreeCopy.next();
        assertEquals(dummyAnnForS1, dummyAnnForS1Copy);
        assertNotSame(dummyAnnForS1, dummyAnnForS1Copy);

        Annotation dummyAnnForS2Copy = annotationsBalloooTreeCopy.next();
        assertEquals(dummyAnnForS2, dummyAnnForS2Copy);
        assertNotSame(dummyAnnForS2, dummyAnnForS2Copy);

        Annotation numberAnnForS2Copy = annotationsBalloooTreeCopy.next();
        assertEquals(numberAnnForS2, numberAnnForS2Copy);
        assertNotSame(numberAnnForS2, numberAnnForS2Copy);
        IntegerFieldValue integerValForS2Copy = (IntegerFieldValue) numberAnnForS2Copy.getFieldValue();
        assertEquals(integerValForS2, integerValForS2Copy);
        assertNotSame(integerValForS2, integerValForS2Copy);

        Annotation motherAnnForS2Copy = annotationsBalloooTreeCopy.next();
        assertEquals(motherAnnForS2, motherAnnForS2Copy);
        assertNotSame(motherAnnForS2, motherAnnForS2Copy);
        Struct motherValForS2Copy = (Struct) motherAnnForS2Copy.getFieldValue();
        assertEquals(motherValForS2, motherValForS2Copy);
        assertNotSame(motherValForS2, motherValForS2Copy);

        Annotation dummyAnnForS3Copy = annotationsBalloooTreeCopy.next();
        assertEquals(dummyAnnForS3, dummyAnnForS3Copy);
        assertNotSame(dummyAnnForS3, dummyAnnForS3Copy);

        Annotation numberAnnForS3Copy = annotationsBalloooTreeCopy.next();
        assertEquals(numberAnnForS3, numberAnnForS3Copy);
        assertNotSame(numberAnnForS3, numberAnnForS3Copy);
        IntegerFieldValue integerValForS3Copy = (IntegerFieldValue) numberAnnForS3Copy.getFieldValue();
        assertEquals(integerValForS3, integerValForS3Copy);
        assertNotSame(integerValForS3, integerValForS3Copy);

        Annotation dummyAnnForS5Copy = annotationsBalloooTreeCopy.next();
        assertEquals(dummyAnnForS5, dummyAnnForS5Copy);
        assertNotSame(dummyAnnForS5, dummyAnnForS5Copy);

        Annotation daughterAnnForS6Copy = annotationsBalloooTreeCopy.next();
        assertEquals(daughterAnnForS6, daughterAnnForS6Copy);
        assertNotSame(daughterAnnForS6, daughterAnnForS6Copy);
        Struct daughterValForS6Copy = (Struct) daughterAnnForS6Copy.getFieldValue();
        assertEquals(daughterValForS6, daughterValForS6Copy);
        assertNotSame(daughterValForS6, daughterValForS6Copy);
        AnnotationReference refFromS6ToMotherAnnCopy = (AnnotationReference) daughterValForS6Copy.getFieldValue("related");
        assertEquals(refFromS6ToMotherAnn, refFromS6ToMotherAnnCopy);
        assertNotSame(refFromS6ToMotherAnn, refFromS6ToMotherAnnCopy);

        assertEquals(str, strCopy);

    }

    @Test
    public void testCyclicReferences() {
        DocumentTypeManager docMan = new DocumentTypeManager();
        AnnotationTypeRegistry reg = docMan.getAnnotationTypeRegistry();

        StringFieldValue strfval = new StringFieldValue("hohohoho");
        SpanList root = new SpanList();
        SpanTree tree = new SpanTree("habahaba", root);
        strfval.setSpanTree(tree);

        //set up types:
        AnnotationType endTagType = new AnnotationType("endtag");
        AnnotationType beginTagType = new AnnotationType("begintag");
        AnnotationReferenceDataType refToBeginTagDataType = new AnnotationReferenceDataType(beginTagType);
        AnnotationReferenceDataType refToEndTagDataType = new AnnotationReferenceDataType(endTagType);
        endTagType.setDataType(refToBeginTagDataType);
        beginTagType.setDataType(refToEndTagDataType);

        //register types:
        reg.register(endTagType);
        reg.register(beginTagType);
        docMan.register(refToBeginTagDataType);
        docMan.register(refToEndTagDataType);

        //set up annotations and their references to each other:
        AnnotationReference refToBeginTag = new AnnotationReference(refToBeginTagDataType);
        AnnotationReference refToEndTag = new AnnotationReference(refToEndTagDataType);
        Annotation beginTag = new Annotation(beginTagType, refToEndTag);
        Annotation endTag = new Annotation(endTagType, refToBeginTag);
        refToBeginTag.setReference(beginTag);
        refToEndTag.setReference(endTag);

        //create spans:
        Span beginTagSpan = new Span(0, 1);
        Span endTagSpan = new Span(1, 1);
        root.add(beginTagSpan);
        root.add(endTagSpan);

        //annotate spans:
        tree.annotate(beginTagSpan, beginTag);
        tree.annotate(endTagSpan, endTag);


        //none of the below statements should lead to a StackOverflowError:
        assertFalse(endTag.toString().equals(beginTag.toString()));

        assertTrue(endTag.hashCode() != beginTag.hashCode());

        assertFalse(endTag.equals(beginTag));
        assertFalse(beginTag.equals(endTag));

        StringFieldValue copyString = strfval.clone();
        assertEquals(strfval, copyString);


        GrowableByteBuffer buffer = new GrowableByteBuffer(1024);
        DocumentSerializer serializer = DocumentSerializerFactory.create6(buffer);

        serializer.write(new Field("stringfield", DataType.STRING), strfval);
        buffer.flip();

        DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(docMan, buffer);
        StringFieldValue stringFieldValue2 = new StringFieldValue();
        deserializer.read(new Field("stringfield", DataType.STRING), stringFieldValue2);

        assertEquals(strfval, stringFieldValue2);
        assertNotSame(strfval, stringFieldValue2);
    }
}
