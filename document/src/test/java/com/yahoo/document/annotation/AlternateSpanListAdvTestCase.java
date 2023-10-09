// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:mpraveen@yahoo-inc.com">Praveen Mohan</a>
 *
 * This test covers all possible scenarios in AlternateSpanList.
 * If you really want to debug, just turn on the debug flag to true.
 *
 */
public class AlternateSpanListAdvTestCase {

    private AnnotationType at1 = new AnnotationType("person", DataType.STRING);
    private AnnotationType at2 = new AnnotationType("street", DataType.STRING);
    private AnnotationType at3 = new AnnotationType("city", DataType.STRING);

    private StringFieldValue fv1 = (StringFieldValue) at1.getDataType().createFieldValue();
    private StringFieldValue fv2 = (StringFieldValue) at2.getDataType().createFieldValue();
    private StringFieldValue fv3 = (StringFieldValue) at3.getDataType().createFieldValue();
    private StringFieldValue fv11 = (StringFieldValue) at1.getDataType().createFieldValue();
    private StringFieldValue fv22 = (StringFieldValue) at2.getDataType().createFieldValue();
    private StringFieldValue fv33 = (StringFieldValue) at3.getDataType().createFieldValue();
    private StringFieldValue fv111 = (StringFieldValue) at1.getDataType().createFieldValue();
    private StringFieldValue fv222 = (StringFieldValue) at1.getDataType().createFieldValue();

    private SpanList root;
    private SpanTree tree;
    private SpanNode span1, span2, span3;
    private SpanNode span11, span22, span33;
    private SpanList alternate1, alternate2, branch0;
    private List<SpanNode> subtreeList1, subtreeList2;
    private AlternateSpanList branch;
    private AlternateSpanList branch3, branch2;
    private Annotation an1;

    private boolean debug = false;

    @Before
    public void buildTree_List() {
        root = new SpanList();
        tree = new SpanTree("test", root);
        branch = new AlternateSpanList();
        span1 = new Span(0, 3);
        span2 = new Span(1, 9);
        span3 = new Span(12, 10);

        span11 = new Span(0, 3);
        span22 = new Span(1, 9);
        span33 = new Span(12, 10);

        an1 = new Annotation(at1, fv1);
        Annotation an2 = new Annotation(at2, fv2);
        Annotation an3 = new Annotation(at3, fv3);
        Annotation an11 = new Annotation(at1, fv11);
        Annotation an22 = new Annotation(at2, fv22);
        Annotation an33 = new Annotation(at3, fv33);

        alternate1 = new SpanList();
        alternate1.add(span3);
        alternate1.add(span2);
        alternate1.add(span1);

        alternate2 = new SpanList();
        alternate2.add(span11);
        alternate2.add(span22);
        alternate2.add(span33);

        tree.annotate(span1, an1);
        tree.annotate(span2, an2);
        tree.annotate(span3, an3);

        tree.annotate(span11, an11);
        tree.annotate(span22, an22);
        tree.annotate(span33, an33);

        subtreeList1 = new ArrayList<SpanNode>();
        subtreeList1.add(alternate1);

        subtreeList2 = new ArrayList<SpanNode>();
        subtreeList2.add(alternate2);
        branch.clearChildren();
        branch.addChildren(1, subtreeList1, 20.0d);
        branch.addChildren(2, subtreeList2, 50.0d);

        root.add(branch);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void assertInvalidGetFrom() {
        assertEquals(0, branch.getFrom(50));
    }

    @Test (expected = IllegalStateException.class)
    public void assertSharingAnnotationInstance() {
        SpanNode testNode = new Span(0, 2);
        tree.annotate(testNode, an1);
    }

    @Test (expected = IllegalStateException.class)
    public void assertSharingSpanTreeRoot() {
        tree = new SpanTree("dummy", root);
    }


    @Test (expected = IllegalStateException.class)
    public void assertAddSameNodeTwice() {
        root.add(branch);
    }

    @Test (expected = IndexOutOfBoundsException.class)
    public void assertInvalidAdd() {
        SpanNode newNode = new Span(10, 10);
        branch.add(branch.getNumSubTrees(), newNode);
    }

    @Test (expected = IndexOutOfBoundsException.class)
    public void assertInvalidChildIteratorIndex() {
        branch.childIterator(branch.getNumSubTrees());
    }

    @Test (expected = IllegalStateException.class)
    public void assertReuseRemovedNode() {
        alternate1.remove(span1);
        branch.add(span1);
    }

    @Test
    public void assertTree_NodeSet1() {
        if (debug) consumeAnnotations(tree, root);
        assertEquals(-1, root.getFrom());
        assertEquals(-1, root.getTo());
        assertEquals(-1, branch.getFrom());
        assertEquals(-1, branch.getTo());
        assertEquals(0, branch.getFrom(1));
        assertEquals(22, branch.getTo(1));
        assertEquals(0, branch.getFrom(2));
        assertEquals(22, branch.getTo(2));
        assertEquals(3, branch.getNumSubTrees());
        int no = branch.getNumSubTrees();

        TreeSet<Double> set = new TreeSet<Double>();
        for (int i = 0; i < no; i ++) {
            double prob = branch.getProbability(i);
            set.add(prob);
        }

        branch.sortSubTreesByProbability();

        Iterator<Double> iter = set.descendingIterator();
        for (int i = 0; i < no; i ++) {
            double prob = branch.getProbability(i);
            double prob1 = iter.next();
            assertTrue(prob == prob1);
        }
        branch.normalizeProbabilities();
        double highest = 0;
        for (int i = 0; i < no; i ++) {
            double prob = branch.getProbability(i);
            if (i == 0) {
                highest = prob;
                continue;
            }
            assertFalse(prob >= highest || prob > 1.0);
            highest = prob;
        }

        ListIterator<SpanNode> it = branch.childIterator();
        assertSame(alternate2, it.next());
        assertSame(alternate1, it.next());
        assertFalse(it.hasNext());

        SpanNode sn;

        it = branch.childIteratorRecursive();
        assertSame(span11, it.next());
        assertSame(span22, it.next());
        assertSame(span33, it.next());
        assertSame(alternate2, it.next());
        assertSame(span3, it.next());
        assertSame(span2, it.next());
        assertSame(span1, it.next());
        assertSame(alternate1, it.next());
        assertFalse(it.hasNext());


        it = branch.childIterator(1);
        assertSame(alternate1, it.next());
        assertFalse(it.hasNext());


        SpanNode snNew = new Span(15, 20);
        List<SpanNode> alternate3 = new ArrayList<SpanNode>();
        alternate3.add(snNew);
        List<SpanNode> l = branch.setChildren(0, alternate3, 200.0d);
        assertFalse (l.get(0) != alternate2);
        assertFalse (branch.getProbability(0) != 200.0);

        String s = branch.toString();
        tree.cleanup();
        boolean dMode = debug;
        debug = false;
        consumeAnnotations(tree, root);
        debug = dMode;

        branch.setProbability(0, 125.0d);
        branch.setProbability(1, 75.0d);
        branch.setProbability(2, 25.0d);
        branch.sortSubTreesByProbability();
        double initial = 125.0;
        for (int j = 0; j < no; j++) {
            double prob = branch.getProbability(j);
            assertFalse (prob != initial);
            initial = initial - 50.0d;
        }
        no = branch.getNumSubTrees();

        for (int j = 0; j < no; no --) {
            branch.removeChildren(j);
        }

        assertFalse (branch.getNumSubTrees() != 1);
        assertFalse(branch.getProbability(0) != 1.0);
        branch.addChildren(1, subtreeList1, 20.0d);
        branch.addChildren(2, subtreeList2, 50.0d);

        no = branch.getNumSubTrees();
        assertFalse (no != 3);
        branch.clearChildren();
        assertFalse (branch.getNumSubTrees() != 3);
        assertFalse(branch.getProbability(0) != 1.0);

        branch.addChildren(1, subtreeList1, 20.0d);
        branch.addChildren(2, subtreeList2, 50.0d);
        no = branch.getNumSubTrees();
        assertFalse (no != 5);
        branch.clearChildren(1);
        assertTrue (branch.getNumSubTrees() == no);
        assertEquals(branch.getFrom(1), -1);
        assertEquals(branch.getTo(1), -1);
        assertTrue(branch.getProbability(1) == 20.0);

        ListIterator<SpanNode> lit = branch.childIteratorRecursive(1);
        assertFalse(lit.hasNext());
        SpanNode newNode = new Span(10, 10);
        branch.add(0, newNode);
        lit = branch.childIteratorRecursive();
        assertTrue(lit.hasNext());
        assertFalse(lit.next() != newNode);

        branch.removeChildren(1);
        assertTrue (branch.getNumSubTrees() == (no-1));

        branch.removeChildren();
        no = branch.getNumSubTrees();
        assertTrue (no == 1);
        assertTrue (branch.getProbability(0) == 1.0);
        assertEquals(branch.getFrom(), -1);
        assertEquals(branch.getTo(), -1);

        buildTree_List();

        CharSequence fieldValue = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        String actual = branch.getText(1, fieldValue).toString();
        String expected = "MNOPQRSTUVBCDEFGHIJABC";
        assertEquals(actual, expected);
    }

    @Test
    public void assertTree_NodeSet2() {
        root = new SpanList();
        tree = new SpanTree("test", root);
        branch = new AlternateSpanList();

        Annotation an111 = new Annotation(at1, fv111);
        Annotation an222 = new Annotation(at2, fv222);

        branch0 = new SpanList();
        SpanNode span1 = new Span(0, 3);
        SpanNode span2 = new Span(3, 3);
        branch0.add(span1);
        branch0.add(span2);
        branch.add(branch0);

        ArrayList<SpanNode> list1 = new ArrayList<SpanNode>();
        branch2 = new AlternateSpanList();
        SpanNode sn1 = new Span(6, 4);
        SpanNode sn2 = new Span(10, 4);
        branch2.add(sn1);
        branch2.add(sn2);
        list1.add(branch2);
        branch.setProbability(0, 20.0d);
        branch.addChildren(1, list1, 30.0d);

        branch3 = new AlternateSpanList();
        SpanNode sn3 = new Span(15, 5);
        SpanNode sn4 = new Span(20, 5);
        ArrayList<SpanNode> list2 = new ArrayList<SpanNode>();
        list2.add(sn3);
        list2.add(sn4);
        branch3.addChildren(1, list2, 20.0d);
        branch3.setProbability(0, 10.0d);

        List<SpanNode> list3 = new ArrayList<SpanNode>();
        list3.add(branch3);
        branch2.addChildren(1, list3, 50.0d);
        branch2.setProbability(0, 25.0d);

        SpanNode sn5 = new Span(25, 3);
        branch3.add(sn5);

        root.add(branch);

        // Never bother. Just for debugging.
        if (debug) {
            System.out.println("===========NodeSet2 ================");
            consumeAnnotations(tree, root);
        }

        assertEquals(0, root.getFrom());
        assertEquals(6, root.getTo());
        assertEquals(0, branch.getFrom());
        assertEquals(6, branch.getTo());
        assertEquals(6, branch2.getFrom());
        assertEquals(14, branch2.getTo());
        assertEquals(25, branch2.getFrom(1));
        assertEquals(28, branch2.getTo(1));
        assertEquals(25, branch3.getFrom());
        assertEquals(28, branch3.getTo());
        assertEquals(15, branch3.getFrom(1));
        assertEquals(25, branch3.getTo(1));
        assertFalse ((branch.getNumSubTrees() != branch2.getNumSubTrees()) ||
            (branch.getNumSubTrees() != branch3.getNumSubTrees()));

        branch3.sortSubTreesByProbability();
        assertEquals(15, branch3.getFrom());
        assertEquals(25, branch3.getTo());
        assertEquals(25, branch3.getFrom(1));
        assertEquals(28, branch3.getTo(1));
    }


    @After
    public void removeTree() {
        tree = null;
    }

    public void consumeAnnotations(SpanTree tree, SpanList root) {
        if (debug) System.out.println("\n\nSpanList: [" + root.getFrom() + ", " + root.getTo() + "] num Children: " + root.numChildren());
        if (debug) System.out.println("-------------------");
        Iterator<SpanNode> childIterator = root.childIterator();
        while (childIterator.hasNext()) {
            SpanNode node = childIterator.next();
            if (debug) System.out.println("\n\nSpan Node (" + node + "): [" + node.getFrom() + ", " + node.getTo() + "] ");
            if (node instanceof AlternateSpanList) {
                parseAlternateLists(tree, (AlternateSpanList)node);
                if (debug) System.out.println("---- Alternate SpanList complete ---");
            } else if (node instanceof SpanList) {
                if (debug) System.out.println("Encountered another span list");
                SpanList spl = (SpanList) node;
                ListIterator<SpanNode> lli = spl.childIterator();
                while (lli.hasNext()) System.out.print(" " + lli.next() + " ");
                consumeAnnotations(tree, (SpanList) node);
            } else {
                   if (debug) System.out.println("\nGetting annotations for this span node: [" + node.getFrom() + ", " + node.getTo() + "] ");
                   getAnnotationsForNode(tree, node);
            }
        }
        if (debug) System.out.println("\nGetting annotations for the SpanList itself : [" + root.getFrom() + ", " + root.getTo() + "] ");
        getAnnotationsForNode(tree, root);
    }

    public void parseAlternateLists(SpanTree tree, AlternateSpanList aspl) {
        int no = aspl.getNumSubTrees();
        if (debug) System.out.println("Parsing Alternate span list. No of subtrees: " + no);
        int ctr = 0;
        while (ctr < no) {
            if (debug) System.out.println("\nSubTree: " + ctr);
            ListIterator<SpanNode> lIter = aspl.childIteratorRecursive(ctr);
            while (lIter.hasNext()) {
                SpanNode spnNode = lIter.next();
                if (debug) System.out.println("Parsing span node: [" + spnNode.getFrom() + ", " + spnNode.getTo() + "] ");
                if (spnNode instanceof AlternateSpanList) {
                    if (debug) System.out.println("A child alternate span list found. Recursing");
                    parseAlternateLists(tree, (AlternateSpanList)spnNode);
                }

                getAnnotationsForNode(tree, spnNode);
            }
            ctr ++;
        }
    }

    public void getAnnotationsForNode(SpanTree tree, SpanNode node) {
        Iterator<Annotation> iter = tree.iterator(node);
        boolean annotationPresent = false;
        while (iter.hasNext()) {
            annotationPresent = true;
            Annotation xx = iter.next();
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
