// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:mpraveen@yahoo-inc.com">Praveen Mohan</a>
 *
 * This test checks if sub-trees are sorted appropriately by probability
 * within an alternate span list and getFrom() and getTo() are updated properly
 * when the trees are sorted.
 *
 */
public class Bug4164299TestCase {

    private SpanTree tree;

    @Before
    public void buildTree() {
        SpanList root = new SpanList();
        AlternateSpanList branch = new AlternateSpanList();
        tree = new SpanTree("test", root);

        SpanNode span1 = new Span(0, 2);
        SpanNode span2 = new Span(2, 2);
        SpanNode span3 = new Span(4, 2);

        SpanNode span11 = new Span(10, 2);
        SpanNode span22 = new Span(12, 2);
        SpanNode span33 = new Span(14, 2);

        branch.add(span3);
        branch.add(span2);
        branch.add(span1);

        List<SpanNode> subtreeList = new ArrayList<SpanNode>();
        subtreeList.add(span11);
        subtreeList.add(span22);
        subtreeList.add(span33);
        branch.addChildren(1, subtreeList, 50.0d);
        root.add(branch);

    }

    @Test
    public void assertTree() {
        final AlternateSpanList branch =  (AlternateSpanList)((SpanList)tree.getRoot()).children().get(0);
        assertEquals(0, branch.getFrom());
        assertEquals(6, branch.getTo());
        assertEquals(10, branch.getFrom(1));
        assertEquals(16, branch.getTo(1));
        branch.sortSubTreesByProbability();

        assertEquals(10, branch.getFrom());
        assertEquals(16, branch.getTo());
        assertEquals(0, branch.getFrom(1));
        assertEquals(6, branch.getTo(1));
    }

    @After
    public void removeTree() {
        tree = null;
    }

    public void consumeAnnotations(SpanTree tree, SpanList root) {
        System.out.println("\n\nSpanList: [" + root.getFrom() + ", " + root.getTo() + "] num Children: " + root.numChildren());
        System.out.println("-------------------");
        Iterator<SpanNode> childIterator = root.childIterator();
        while (childIterator.hasNext()) {
            SpanNode node = childIterator.next();
            System.out.println("\n\nSpan Node (" + node + "): [" + node.getFrom() + ", " + node.getTo() + "] ");
            if (node instanceof AlternateSpanList) {
                parseAlternateLists(tree, (AlternateSpanList)node);
                System.out.println("---- Alternate SpanList complete ---");
            } else if (node instanceof SpanList) {
                System.out.println("Encountered another span list");
                SpanList spl = (SpanList) node;
                ListIterator<SpanNode> lli = spl.childIterator();
                while (lli.hasNext()) System.out.print(" " + lli.next() + " ");
                consumeAnnotations(tree, (SpanList) node);
            } else {
                   System.out.println("\nGetting annotations for this span node: [" + node.getFrom() + ", " + node.getTo() + "] ");
                   getAnnotationsForNode(tree, node);
            }
        }
        System.out.println("\nGetting annotations for the SpanList itself : [" + root.getFrom() + ", " + root.getTo() + "] ");
        getAnnotationsForNode(tree, root);
    }

    public void parseAlternateLists(SpanTree tree, AlternateSpanList aspl) {
        int no = aspl.getNumSubTrees();
        System.out.println("Parsing Alternate span list. No of subtrees: " + no);
        int ctr = 0;
        while (ctr < no) {
            System.out.println("\nSubTree: " + ctr);
            ListIterator<SpanNode> lIter = aspl.childIteratorRecursive(ctr);
            while (lIter.hasNext()) {
                SpanNode spnNode = lIter.next();
                System.out.println("Parsing span node: [" + spnNode.getFrom() + ", " + spnNode.getTo() + "] ");
                if (spnNode instanceof AlternateSpanList) {
                    System.out.println("A child alternate span list found. Recursing");
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
            System.out.println("Annotation: " + xx);
            if (fValue == null) {
                System.out.println("Field Value is null");
                return;
            } else {
                System.out.println("Field Value: " + fValue.getString());
            }
        }
        if (!annotationPresent) {
            System.out.println("****No annotations found for the span node: [" + node.getFrom() + ", " + node.getTo() + "] ");
        }
    }
}
