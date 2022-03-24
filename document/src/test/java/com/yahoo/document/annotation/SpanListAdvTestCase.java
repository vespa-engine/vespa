// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Collection;
import java.util.ListIterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:mpraveen@yahoo-inc.com">Praveen Mohan</a>
 *
 * This test covers all possible scenarios in a SpanList.
 *
 */

public class SpanListAdvTestCase {

    private boolean debug = false;

    private AnnotationType at1 = new AnnotationType("person", DataType.STRING);
    private AnnotationType at2 = new AnnotationType("street", DataType.STRING);
    private AnnotationType at3 = new AnnotationType("city", DataType.STRING);

    private SpanList root = new SpanList();
    public SpanTree tree = new SpanTree("test", root);

    private SpanNode span1, span2, span3;
    private SpanNode span11, span22, span33;
    private SpanNode span111, span222, span333;
    private SpanList alternate1, alternate2, alternate3;

    @Before
    public void buildTree() {
        tree.cleanup();
        span1 = new Span(0, 3);
        span2 = new Span(4, 6);
        span3 = new Span(6, 10);

        span11 = new Span(0, 2);
        span22 = new Span(2, 10);
        span33 = new Span(12, 10);

        span111 = new Span(5, 10);
        span222 = new Span(15, 5);
        span333 = new Span(20, 10);

        alternate1 = new SpanList();
        alternate1.add(span3);
        alternate1.add(span2);
        alternate1.add(span1);

        alternate2 = new SpanList();
        alternate2.add(span11);
        alternate2.add(span22);
        alternate2.add(span33);

        alternate3 = new SpanList();
        alternate3.add(span111);
        alternate3.add(span222);

        root.add(span333);

        tree.annotate(span1, at1);
        tree.annotate(span2, at2);
        tree.annotate(span3, at3);

        tree.annotate(span11, at1);
        tree.annotate(span22, at2);
        tree.annotate(span33, at3);

        tree.annotate(span111, at1);
        tree.annotate(span222, at2);
        tree.annotate(span333, at3);

        alternate1.add(alternate3);

        root.add(alternate1);
        root.add(alternate2);
    }

    @Test
    public void assertTree() {

        if (debug) {
            consumeAnnotations((SpanList)tree.getRoot());
        }

        assertEquals(0, root.getFrom());
        assertEquals(30, root.getTo());
        assertEquals(0, alternate1.getFrom());
        assertEquals(20, alternate1.getTo());
        assertEquals(0, alternate2.getFrom());
        assertEquals(22, alternate2.getTo());
        assertEquals(5, alternate3.getFrom());
        assertEquals(20, alternate3.getTo());
        assertFalse(root.numChildren() != 3 || alternate1.numChildren() != 4 || alternate2.numChildren() != 3 || alternate3.numChildren() != 2);

        ArrayList<SpanNode> al = new ArrayList<SpanNode>();
        al.add(span333);
        al.add(alternate1);
        al.add(alternate2);
        ListIterator<SpanNode> iter = root.childIterator();
        while (iter.hasNext()) {
            SpanNode sn = iter.next();
            int i = 0;
            for (i = 0; i < al.size(); i ++) {
                if (sn == al.get(i)) {
                    break;
                }
            }
            assertFalse(i >= al.size());
        }

        iter = root.childIteratorRecursive();
        boolean nodeFound = false;
        while (iter.hasNext()) {
            SpanNode sn = iter.next();
            if (sn == span222 || sn == span111) {
                nodeFound = true;
                break;
            }
        }
        assertTrue(nodeFound);

        alternate1.sortChildren();
        SpanNode ssn = new Span(2, 1);
        alternate1.add(ssn);
        alternate1.sortChildren();

        iter = alternate1.childIterator();
        int from = -1;
        while (iter.hasNext()) {
            SpanNode sn = iter.next();
            if (from == -1) {
                from = sn.getFrom();
                continue;
            }
            assertFalse(sn.getFrom() < from);
            from = sn.getFrom();
        }
        alternate1.remove(ssn);

        SpanList sl = alternate3.remove(span111);
        assertFalse (sl != alternate3);
        assertFalse(span111.isValid());

        alternate1.remove(alternate3);
        assertFalse(alternate3.isValid());
        assertFalse(span222.isValid());

        int noofChild = alternate1.numChildren();
        for (int i = 0; i < alternate1.numChildren(); ) {
            alternate1.remove(i);
        }
        assertFalse(alternate1.numChildren() != 0);

        int noAnnotations = tree.numAnnotations();
        tree.cleanup();
        assertFalse(tree.numAnnotations() != (noAnnotations - 5));

        root.clearChildren();
        tree.cleanup();
        assertFalse(tree.numAnnotations() != 0);

        sl = new SpanList(alternate1);
        root.add(sl);
        assertFalse(root.getFrom() != -1 || root.getTo() != -1);

        SpanNode newSpan1 = new Span(0, 10);
        SpanNode newSpan2 = new Span(12, 8);
        alternate3 = new SpanList();
        alternate3.add(newSpan1);
        alternate3.add(newSpan2);
        alternate2.add(alternate3);

        SpanList newA2 = new SpanList(alternate2);
        root.add(newA2);
        assertFalse(root.getFrom() != newA2.getFrom() || root.getTo() != newA2.getTo());
        assertFalse(newA2.numChildren() != 4);
    }

    @After
    public void removeTree() {
        tree = null;
    }


    public void consumeAnnotations(SpanList root) {
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
