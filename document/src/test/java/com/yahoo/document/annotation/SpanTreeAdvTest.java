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

/**
 * @author <a href="mailto:mpraveen@yahoo-inc.com">Praveen Mohan</a>
 *
 * This test covers all possible scenarios in SpanTree.
 *
 */



public class SpanTreeAdvTest {

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

        span1 = new Span(10, 3);
        span2 = new Span(4, 6);
        span3 = new Span(13, 10);

        span11 = new Span(0, 2);
        span22 = new Span(2, 10);
        span33 = new Span(12, 10);

        span111 = new Span(5, 10);
        span222 = new Span(15, 5);
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

    @Test (expected = IllegalStateException.class)
    public void assertSharingAnnotationInstance() {
        populateSpanTree();
        tree.annotate(span333, an111);
    }



    @Test
    public void assertRemoveChildrenCleanupTest() {
        populateSpanTree();
        root.removeChildren();
        tree.cleanup();
        Iterator<Annotation> iter =tree.iterator();
        assertFalse(iter.hasNext());
        assertEquals(0, tree.numAnnotations());
    }


    @Test
    public void assertClearChildrenCleanupTest() {
        populateSpanTree();
        root.clearChildren();
        tree.cleanup();
        Iterator<Annotation> iter =tree.iterator();
        assertFalse(iter.hasNext());
        assertEquals(0, tree.numAnnotations());
    }

    @Test
    public void assertRemoveChildrenIndexCleanupTest() {
        populateSpanTree();
        int no = tree.numAnnotations();
        root.removeChildren(1);
        tree.cleanup();
        int postNo = tree.numAnnotations();
        assertEquals((no-8), postNo);
    }

    @Test
    public void assertClearChildrenIndexCleanupTest() {
        populateSpanTree();
        int no = tree.numAnnotations();
        root.clearChildren(1);
        tree.cleanup();
        int postNo = tree.numAnnotations();
        assertEquals(3, postNo);
    }


    @Test
    public void assertASPLRemoveCleanupTest() {
        populateSpanTree();
        int no = tree.numAnnotations();
        alternate2.removeChildren(2);
        alternate2.removeChildren(1);
        alternate2.remove(span33);
        tree.cleanup();
        int postNo = tree.numAnnotations();
        assertEquals((no-6), postNo);
    }


    @Test
    public void assertSPLRemoveCleanupTest() {
        populateSpanTree();
        int no = tree.numAnnotations();
        branch.remove(alternate2);
        tree.cleanup();
        int postNo = tree.numAnnotations();
        assertEquals((no-6), postNo);
    }

    @Test
    public void assertIteratorRecursiveASPLTest() {
        populateSpanTree();
        int no = 0;
        Iterator<Annotation> it = tree.iteratorRecursive(alternate2);
        while (it.hasNext()) {
            it.next();
            no ++;
        }
        assertEquals(6, no);
    }

    @Test
    public void assertClearAnnotationsRecursiveASPLTest() {
        populateSpanTree();
        int no = tree.numAnnotations();
        tree.clearAnnotationsRecursive(alternate2);
        int postNo = tree.numAnnotations();
        assertEquals((no-6), postNo);
    }



    @Test (expected = IllegalStateException.class)
    public void assertReuseRemovedNode() {
        populateSpanTree();
        root.remove(span3);
        tree.annotate(span3, new Annotation(at1));
    }

    @Test (expected = IllegalStateException.class)
    public void assertNeedForCleanup() {
        populateSpanTree();
        root.remove(span3);
        Iterator<Annotation> iter =tree.iterator();
        while (iter.hasNext()) {
            SpanNode sn = iter.next().getSpanNode();
        }
    }

    @Test
    public void validateSpanTree() {
        populateSpanTree();
        int no = tree.numAnnotations();
        root.remove(span3);
        tree.cleanup();
        int noAfter = tree.numAnnotations();
        assertEquals((no-1), noAfter);
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
