// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:mpraveen@yahoo-inc.com">Praveen Mohan</a>
 *
 * This test covers all possible scenarios in SpanNode.
 */



public class SpanNodeAdvTestCase {

    private boolean debug = false;

    private AnnotationType at1 = new AnnotationType("person", DataType.STRING);
    private AnnotationType at2 = new AnnotationType("street", DataType.STRING);
    private AnnotationType at3 = new AnnotationType("city", DataType.STRING);

    public SpanTree tree = null;

    private SpanNode span1, span2, span3;
    private SpanNode span11, span22, span33;
    private SpanNode span111, span222, span333;

    private AlternateSpanList root, alternate1, alternate2;
    private SpanList branch, branch1;
    private List<SpanNode> subtreeList1, subtreeList2, subtreeList3, subtreeList4;
    private Annotation an111;


    public void populateSpanTree() {

        root = new AlternateSpanList();
        tree = new SpanTree("test", root);

        span1 = new Span(10, 8);
        span2 = new Span(5, 10);
        span3 = new Span(13, 2);

        span11 = new Span(0, 2);
        span22 = new Span(2, 10);
        span33 = new Span(8, 10);

        span111 = new Span(5, 10);
        span222 = new Span(10, 10);
        span333 = new Span(20, 10);

        an111 = new Annotation(at1);

        tree.annotate(span1, at1).annotate(span2, at2).annotate(span3, at3);
        tree.annotate(span11, at1).annotate(span22, at2).annotate(span33, at3);
        tree.annotate(span111, an111).annotate(span222, at2).annotate(span333, at3).annotate(span333, at1);
        tree.annotate(span222, at2);

        root.add(span3);
        root.add(span2);
        root.add(span1);

        alternate1 = new AlternateSpanList();
        alternate1.add(span11);
        branch = new SpanList();
        branch.add(span22);
        subtreeList1 = new ArrayList<SpanNode>();
        subtreeList1.add(branch);
        alternate1.addChildren(1, subtreeList1, 50.0d);

        alternate2 = new AlternateSpanList();
        alternate2.add(span33);
        subtreeList2 = new ArrayList<SpanNode>();
        subtreeList2.add(span111);
        subtreeList2.add(span222);
        alternate2.addChildren(1, subtreeList2, 70.0d);

        subtreeList3 = new ArrayList<SpanNode>();
        branch1 = new SpanList();
        branch1.add(span333);
        subtreeList3.add(branch1);
        alternate2.addChildren(2, subtreeList3, 90.0d);
        branch.add(alternate2);

        subtreeList4 = new ArrayList<SpanNode>();
        subtreeList4.add(alternate1);
        root.addChildren(1, subtreeList4, 10.0d);
    }

    @Test
    public void assertIsValid() {
        populateSpanTree();
        root.clearChildren(1);
        root.remove(span3);

        assertTrue(root.isValid());
        assertTrue(span1.isValid());
        assertTrue(span2.isValid());

        assertFalse(span3.isValid());
        assertFalse(alternate1.isValid());
        assertFalse(span11.isValid());
        assertFalse(branch.isValid());
        assertFalse(span22.isValid());
        assertFalse(alternate2.isValid());
        assertFalse(span33.isValid());
        assertFalse(branch1.isValid());
        assertFalse(span333.isValid());
        assertFalse(span111.isValid());
        assertFalse(span222.isValid());
    }

    @Test
    public void assertIsLeafNode() {
        populateSpanTree();
        assertFalse(root.isLeafNode());
        assertFalse(alternate1.isLeafNode());
        assertFalse(branch.isLeafNode());
        assertTrue(span11.isLeafNode());
        assertTrue(span22.isLeafNode());
        assertFalse(alternate2.isLeafNode());
        assertTrue(span33.isLeafNode());
        assertFalse(branch1.isLeafNode());
        assertTrue(span111.isLeafNode());
        assertTrue(span222.isLeafNode());
        assertTrue(span333.isLeafNode());
    }

    @Test
    public void assertOverlaps() {
        populateSpanTree();
        assertTrue(span1.overlaps(span2));
        assertTrue(span1.overlaps(span3));
        assertTrue(span2.overlaps(span3));
        assertFalse(span11.overlaps(span22));
        assertTrue(span22.overlaps(span33));
        assertFalse(span11.overlaps(span33));
        assertTrue(span111.overlaps(span222));
        assertFalse(span111.overlaps(span333));
        assertFalse(span222.overlaps(span333));
        assertTrue(span1.overlaps(span222));
        assertFalse(span1.overlaps(span333));
        assertTrue(span2.overlaps(span222));
        assertFalse(span3.overlaps(span22));

        assertTrue(span2.overlaps(span1));
        assertTrue(span3.overlaps(span1));
        assertTrue(span3.overlaps(span2));
        assertFalse(span22.overlaps(span11));
        assertTrue(span33.overlaps(span22));
        assertFalse(span33.overlaps(span11));
        assertTrue(span222.overlaps(span111));
        assertFalse(span333.overlaps(span111));
        assertFalse(span333.overlaps(span222));
        assertTrue(span222.overlaps(span1));
        assertFalse(span333.overlaps(span1));
        assertTrue(span222.overlaps(span2));
        assertTrue(span1.overlaps(span1));
        assertFalse(span22.overlaps(span3));
        assertFalse(root.overlaps(alternate1));
        assertTrue(alternate2.overlaps(span22));
        assertTrue(branch.overlaps(root));
        assertTrue(branch.overlaps(alternate2));
        assertTrue(root.overlaps(alternate2));
        assertTrue(span22.overlaps(root));

    }

    @Test
    public void assertContains() {
        populateSpanTree();
        assertTrue(span222.contains(span1));
        assertFalse(span1.contains(span222));
        assertTrue(span1.contains(span3));
        assertFalse(span33.contains(span111));
        assertTrue(span222.contains(span3));
        assertTrue(span111.contains(span2));
        assertTrue(span2.contains(span111));
        assertTrue(branch.contains(root));
        assertTrue(branch.contains(alternate2));
        assertTrue(root.contains(alternate2));
        assertFalse(alternate2.contains(span22));
    }

    @Test
    public void assertCompareTo() {
        populateSpanTree();
        assertEquals(1 , span1.compareTo(span2));
        assertEquals(-1, span2.compareTo(span1));
        assertEquals(-1 , span2.compareTo(span3));
        assertEquals(1, span3.compareTo(span2));
        assertEquals(0, span2.compareTo(span111));
        assertEquals(1, root.compareTo(branch));
        assertEquals(-1, alternate1.compareTo(root));
        assertEquals(1, branch.compareTo(span22));
        assertEquals(-1, branch.compareTo(alternate2));
        assertEquals(1, alternate2.compareTo(root));
        assertEquals(-1, span111.compareTo(root));
        assertEquals(0, span333.compareTo(branch1));
        assertEquals(0, alternate2.compareTo(span33));
        root.removeChildren();
        tree.cleanup();
        assertEquals(1, span11.compareTo(root));
    }

    @Test
    public void assertGetParent() {
        populateSpanTree();
        assertEquals(root, span1.getParent());
        assertEquals(root, span2.getParent());
        assertEquals(root, span3.getParent());
        assertEquals(root, alternate1.getParent());
        assertEquals(alternate1, span11.getParent());
        assertEquals(alternate1, branch.getParent());
        assertEquals(branch, span22.getParent());
        assertEquals(branch, alternate2.getParent());
        assertEquals(alternate2, span33.getParent());
        assertEquals(alternate2, span111.getParent());
        assertEquals(branch1, span333.getParent());
        assertEquals(alternate2, branch1.getParent());
        assertEquals(alternate1, span11.getParent());
    }


    @After
    public void tearDown() {
        tree = null;
    }


    public void consumeAnnotations(SpanList root) {
        if (root instanceof AlternateSpanList) {
            parseAlternateLists((AlternateSpanList)root);
            if (debug) System.out.println("\nGetting annotations for the SpanList itself : [" + root.getFrom() + ", " + root.getTo() + "] ");
            getAnnotationsForNode(root);
            return;
        }
        if (debug) System.out.println("\n\nSpanList: [" + root.getFrom() + ", " + root.getTo() + "] num Children: " + root.numChildren());
        if (debug) System.out.println("-------------------");
        Iterator<SpanNode> childIterator = root.childIterator();
        while (childIterator.hasNext()) {
            SpanNode node = childIterator.next();
            //System.out.println("Span Node: " + node); // + " Span Text: " + node.getText(fieldValStr));
            if (debug) System.out.println("\n\nSpan Node: [" + node.getFrom() + ", " + node.getTo() + "] ");
            if (node instanceof AlternateSpanList) {
                parseAlternateLists((AlternateSpanList)node);
                if (debug) System.out.println("---- Alternate SpanList complete ---");
            } else if (node instanceof SpanList) {
                if (debug) System.out.println("Encountered another span list");
                SpanList spl = (SpanList) node;
                ListIterator<SpanNode> lli = spl.childIterator();
                while (lli.hasNext()) System.out.print(" " + lli.next() + " ");
                consumeAnnotations((SpanList) node);
            } else {
                if (debug) System.out.println("\nGetting annotations for this span node: [" + node.getFrom() + ", " + node.getTo() + "] ");
                getAnnotationsForNode(node);
            }
        }
        if (debug) System.out.println("\nGetting annotations for the SpanList itself : [" + root.getFrom() + ", " + root.getTo() + "] ");
        getAnnotationsForNode(root);
    }

    public void parseAlternateLists(AlternateSpanList aspl) {
        int no = aspl.getNumSubTrees();
        if (debug) System.out.println("Parsing Alternate span list. No of subtrees: " + no);
        int ctr = 0;
        while (ctr < no) {
            if (debug) System.out.println("\nSubTree: " + ctr + " probability: " + aspl.getProbability(ctr));
            ListIterator<SpanNode> lIter = aspl.childIterator(ctr);
            while (lIter.hasNext()) {
                SpanNode spnNode = lIter.next();
                if (debug) System.out.println("Parsing span node: [" + spnNode.getFrom() + ", " + spnNode.getTo() + "] ");
                if (spnNode instanceof AlternateSpanList) {
                    if (debug) System.out.println("A child alternate span list found. Recursing");
                    parseAlternateLists((AlternateSpanList)spnNode);
                } else if (spnNode instanceof SpanList) {
                    if (debug) System.out.println("A child span list found. Recursing");
                    consumeAnnotations((SpanList)spnNode);
                } else {
                    //System.out.println("Span Node (from alternate spanlist): " + spnNode);
                    getAnnotationsForNode(spnNode);
                }
            }
            ctr ++;
        }
    }


    public void parseFieldForAnnotations(StringFieldValue sfv) {
        Collection<SpanTree> c = sfv.getSpanTrees();
        Iterator<SpanTree> iiter = c.iterator();
        while (iiter.hasNext()) {
            if (debug) System.out.println(sfv + " has annotations");
            tree = iiter.next();
            SpanList root = (SpanList) tree.getRoot();
            consumeAnnotations(root);
        }
    }


    public void getAnnotationsForNode(SpanNode node) {
        Iterator<Annotation> iter = tree.iterator(node);
        boolean annotationPresent = false;
        while (iter.hasNext()) {
            annotationPresent = true;
            Annotation xx = iter.next();
            AnnotationType t = xx.getType();
            StringFieldValue fValue = (StringFieldValue) xx.getFieldValue();
            if (debug) System.out.println("Annotation: " + xx);
            if (fValue == null) {
                if (debug) System.out.println("Field Value is null");
                return;
            } else {
                if (debug) System.out.println("Field Value: " + fValue.getString());
            }
        }
        if (!annotationPresent) {
            if (debug) System.out.println("****No annotations found for the span node: [" + node.getFrom() + ", " + node.getTo() + "] ");
        }
    }
}
