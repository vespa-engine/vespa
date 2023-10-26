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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class Bug4155865TestCase {

    private SpanTree tree;

    @Before
    public void buildTree() {
        AnnotationType at1 = new AnnotationType("person", DataType.STRING);
        AnnotationType at2 = new AnnotationType("street", DataType.STRING);
        AnnotationType at3 = new AnnotationType("city", DataType.STRING);

        StringFieldValue fv1 = (StringFieldValue) at1.getDataType().createFieldValue();
        fv1.assign("Praveen");
        Annotation an1 = new Annotation(at1, fv1);

        StringFieldValue fv2 = (StringFieldValue) at2.getDataType().createFieldValue();
        fv2.assign("Bommanahalli");
        Annotation an2 = new Annotation(at2, fv2);

        StringFieldValue fv3 = (StringFieldValue) at3.getDataType().createFieldValue();
        fv3.assign("Bangalore");
        Annotation an3 = new Annotation(at3, fv3);


        StringFieldValue fv11 = (StringFieldValue) at1.getDataType().createFieldValue();
        fv11.assign("Elancheran");
        Annotation an11 = new Annotation(at1, fv11);

        StringFieldValue fv22 = (StringFieldValue) at2.getDataType().createFieldValue();
        fv22.assign("Kagadaspura");
        Annotation an22 = new Annotation(at2, fv22);

        StringFieldValue fv33 = (StringFieldValue) at3.getDataType().createFieldValue();
        fv33.assign("Delhi");
        Annotation an33 = new Annotation(at3, fv33);

        StringFieldValue fv111 = (StringFieldValue) at1.getDataType().createFieldValue();
        fv111.assign("Govindan");
        Annotation an111 = new Annotation(at1, fv111);

        StringFieldValue fv222 = (StringFieldValue) at1.getDataType().createFieldValue();
        fv222.assign("Kenneth");
        Annotation an222 = new Annotation(at2, fv222);

        SpanList root = new SpanList();
        tree = new SpanTree("test", root);
        AlternateSpanList branch = new AlternateSpanList();
        SpanList branch2 = new SpanList();


        SpanNode sn1 = new Span(0, 5);
        SpanNode sn2 = new Span(5, 10);
        SpanNode sn3 = new Span(15, 10);
        SpanNode sn4 = new Span(15, 20);

        SpanNode span1 = new Span(0, 3);
        SpanNode span2 = new Span(1, 9);
        SpanNode span3 = new Span(12, 10);

        root.add(sn4);
        root.add(sn3);
        root.add(sn2);
        root.add(sn1);

        SpanNode spn1 = new Span(4, 5);
        branch2.add(spn1);

        AlternateSpanList branch3 = new AlternateSpanList();

        SpanNode spn2 = new Span(1, 4);
        SpanNode spn3 = new Span(6, 10);
        tree.annotate(spn2, an111);
        tree.annotate(spn3, an222);

        List<SpanNode> stList = new ArrayList<SpanNode>();
        stList.add(spn2);
        List<SpanNode> stList1 = new ArrayList<SpanNode>();
        stList1.add(spn3);
        branch3.addChildren(stList, 45.0);
        branch3.addChildren(stList1, 25.0);

        root.add(branch2);
        branch.add(branch3);
        root.add(branch);

        SpanNode span11 = new Span(0, 3);
        SpanNode span22 = new Span(1, 9);
        SpanNode span33 = new Span(12, 10);

        SpanList alternate2 = new SpanList();
        alternate2.add(span3);
        alternate2.add(span2);
        alternate2.add(span1);

        SpanList alternate1 = new SpanList();
        alternate1.add(span11);
        alternate1.add(span22);
        alternate1.add(span33);

        tree.annotate(span1, an1);
        tree.annotate(span2, an2);
        tree.annotate(span3, an3);

        tree.annotate(span11, an11);
        tree.annotate(span22, an22);
        tree.annotate(span33, an33);

        List<SpanNode> subtreeList1 = new ArrayList<SpanNode>();
        subtreeList1.add(alternate1);

        List<SpanNode> subtreeList2 = new ArrayList<SpanNode>();
        subtreeList2.add(alternate2);

        branch.addChildren(subtreeList1, 50.0d);
        branch.addChildren(subtreeList2, 100.0d);
    }

    @Test
    public void assertTree() {
        final SpanList root = (SpanList) tree.getRoot();
        //Level 0:
        assertEquals(0, root.getFrom());
        assertEquals(35, root.getTo());
        assertEquals(6, root.numChildren());

        //Level 1:
        assertTrue(root.children().get(0) instanceof Span);
        assertEquals(15, root.children().get(0).getFrom());
        assertEquals(35, root.children().get(0).getTo());
        assertFalse(root.children().get(0).childIterator().hasNext());
        assertFalse(tree.iterator(root.children().get(0)).hasNext());

        assertTrue(root.children().get(1) instanceof Span);
        assertEquals(15, root.children().get(1).getFrom());
        assertEquals(25, root.children().get(1).getTo());
        assertFalse(root.children().get(1).childIterator().hasNext());
        assertFalse(tree.iterator(root.children().get(1)).hasNext());

        assertTrue(root.children().get(2) instanceof Span);
        assertEquals(5, root.children().get(2).getFrom());
        assertEquals(15, root.children().get(2).getTo());
        assertFalse(root.children().get(2).childIterator().hasNext());
        assertFalse(tree.iterator(root.children().get(2)).hasNext());

        assertTrue(root.children().get(3) instanceof Span);
        assertEquals(0, root.children().get(3).getFrom());
        assertEquals(5, root.children().get(3).getTo());
        assertFalse(root.children().get(3).childIterator().hasNext());
        assertFalse(tree.iterator(root.children().get(3)).hasNext());

        assertTrue(root.children().get(4) instanceof SpanList);
        assertFalse(root.children().get(4) instanceof AlternateSpanList);
        assertEquals(4, root.children().get(4).getFrom());
        assertEquals(9, root.children().get(4).getTo());
        assertTrue(root.children().get(4).childIterator().hasNext());
        assertFalse(tree.iterator(root.children().get(4)).hasNext());

        assertTrue(root.children().get(5) instanceof AlternateSpanList);
        assertEquals(-1, root.children().get(5).getFrom());
        assertEquals(-1, root.children().get(5).getTo());
        assertTrue(root.children().get(5).childIterator().hasNext());
        assertFalse(tree.iterator(root.children().get(5)).hasNext());


        //Level 2:
        final SpanList list1 = (SpanList) root.children().get(4);
        assertFalse(list1 instanceof AlternateSpanList);
        assertEquals(1, list1.numChildren());

        assertTrue(list1.children().get(0) instanceof Span);
        assertEquals(4, list1.children().get(0).getFrom());
        assertEquals(9, list1.children().get(0).getTo());
        assertFalse(list1.children().get(0).childIterator().hasNext());
        assertFalse(tree.iterator(list1.children().get(0)).hasNext());

        final AlternateSpanList altList1 = (AlternateSpanList) root.children().get(5);
        assertEquals(3, altList1.getNumSubTrees());

        List<SpanNode> subTree0 = altList1.children(0);
        assertEquals(1, subTree0.size());

        assertTrue(subTree0.get(0) instanceof AlternateSpanList); //TODO: Assert on this!!
        assertEquals(3, ((AlternateSpanList) subTree0.get(0)).getNumSubTrees());
        List<SpanNode> subTree0_0 = ((AlternateSpanList) subTree0.get(0)).children(0);
        assertEquals(0, subTree0_0.size());
        List<SpanNode> subTree0_1 = ((AlternateSpanList) subTree0.get(0)).children(1);
        assertEquals(1, subTree0_1.size());
        List<SpanNode> subTree0_2 = ((AlternateSpanList) subTree0.get(0)).children(2);
        assertEquals(1, subTree0_2.size());

        assertEquals(-1, subTree0.get(0).getFrom());
        assertEquals(-1, subTree0.get(0).getTo());
        assertTrue(subTree0.get(0).childIterator().hasNext());
        assertFalse(tree.iterator(subTree0.get(0)).hasNext());

        List<SpanNode> subTree1 = altList1.children(1);
        assertEquals(1, subTree1.size());

        assertTrue(subTree1.get(0) instanceof SpanList);
        assertFalse(subTree1.get(0) instanceof AlternateSpanList);
        assertEquals(0, subTree1.get(0).getFrom());
        assertEquals(22, subTree1.get(0).getTo());
        assertTrue(subTree1.get(0).childIterator().hasNext());
        assertFalse(tree.iterator(subTree1.get(0)).hasNext());

        List<SpanNode> subTree2 = altList1.children(2);
        assertEquals(1, subTree2.size());

        assertTrue(subTree2.get(0) instanceof SpanList);
        assertFalse(subTree2.get(0) instanceof AlternateSpanList);
        assertEquals(0, subTree2.get(0).getFrom());
        assertEquals(22, subTree2.get(0).getTo());
        assertTrue(subTree2.get(0).childIterator().hasNext());
        assertFalse(tree.iterator(subTree2.get(0)).hasNext());

        //NOTE subTree2 has children


        //Level 3
        assertTrue(subTree0_0.isEmpty());

        final Span subTree0_1_0 = (Span) subTree0_1.get(0);
        assertEquals(1, subTree0_1_0.getFrom());
        assertEquals(5, subTree0_1_0.getTo());
        assertFalse(subTree0_1_0.childIterator().hasNext());
        final Iterator<Annotation> subTree0_1_0AnnIterator = tree.iterator(subTree0_1_0);
        assertTrue(subTree0_1_0AnnIterator.hasNext());
        Annotation subTree0_1_0Annotation = subTree0_1_0AnnIterator.next();
        //TODO: Assert on annotation
        assertFalse(subTree0_1_0AnnIterator.hasNext());


        final Span subTree0_2_0 = (Span) subTree0_2.get(0);
        assertEquals(6, subTree0_2_0.getFrom());
        assertEquals(16, subTree0_2_0.getTo());
        assertFalse(subTree0_2_0.childIterator().hasNext());
        final Iterator<Annotation> subTree0_2_0AnnIterator = tree.iterator(subTree0_2_0);
        assertTrue(subTree0_2_0AnnIterator.hasNext());
        Annotation subTree0_2_0Annotation = subTree0_2_0AnnIterator.next();
        //TODO: Assert on annotation
        assertFalse(subTree0_2_0AnnIterator.hasNext());


        final SpanList sl = (SpanList) subTree1.get(0);
        assertFalse(sl instanceof AlternateSpanList);
        assertEquals(3, sl.children().size());

        assertTrue(sl.children().get(0) instanceof Span);
        assertEquals(0, sl.children().get(0).getFrom());
        assertEquals(3, sl.children().get(0).getTo());
        assertFalse(sl.children().get(0).childIterator().hasNext());
        final Iterator<Annotation> iterator0 = tree.iterator(sl.children().get(0));
        assertTrue(iterator0.hasNext());
        Annotation iterator0Annotation = iterator0.next();
        //TODO: Assert on annotation
        assertFalse(iterator0.hasNext());

        assertTrue(sl.children().get(1) instanceof Span);
        assertEquals(1, sl.children().get(1).getFrom());
        assertEquals(10, sl.children().get(1).getTo());
        assertFalse(sl.children().get(1).childIterator().hasNext());
        final Iterator<Annotation> iterator1 = tree.iterator(sl.children().get(1));
        assertTrue(iterator1.hasNext());
        Annotation iterator1Annotation = iterator1.next();
        //TODO: Assert on annotation
        assertFalse(iterator1.hasNext());

        assertTrue(sl.children().get(2) instanceof Span);
        assertEquals(12, sl.children().get(2).getFrom());
        assertEquals(22, sl.children().get(2).getTo());
        assertFalse(sl.children().get(2).childIterator().hasNext());
        final Iterator<Annotation> iterator2 = tree.iterator(sl.children().get(2));
        assertTrue(iterator2.hasNext());
        Annotation iterator2Annotation = iterator2.next();
        //TODO: Assert on annotation
        assertFalse(iterator2.hasNext());

        final SpanList sl2 = (SpanList) subTree2.get(0);
        assertFalse (sl2 instanceof AlternateSpanList);
        assertEquals(3, sl2.children().size());

        assertTrue(sl2.children().get(0) instanceof Span);
        assertEquals(12, sl2.children().get(0).getFrom());
        assertEquals(22, sl2.children().get(0).getTo());
        assertFalse(sl2.children().get(0).childIterator().hasNext());
        final Iterator<Annotation> iterator3 = tree.iterator(sl2.children().get(0));
        assertTrue(iterator3.hasNext());
        Annotation iterator3Annotation = iterator3.next();
        //TODO: Assert on annotation
        assertFalse(iterator3.hasNext());

        assertTrue(sl2.children().get(1) instanceof Span);
        assertEquals(1, sl2.children().get(1).getFrom());
        assertEquals(10, sl2.children().get(1).getTo());
        assertFalse(sl2.children().get(1).childIterator().hasNext());
        final Iterator<Annotation> iterator4 = tree.iterator(sl2.children().get(1));
        assertTrue(iterator4.hasNext());
        Annotation iterator4Annotation = iterator4.next();
        //TODO: Assert on annotation
        assertFalse(iterator4.hasNext());

        assertTrue(sl2.children().get(2) instanceof Span);
        assertEquals(0, sl2.children().get(2).getFrom());
        assertEquals(3, sl2.children().get(2).getTo());
        assertFalse(sl2.children().get(2).childIterator().hasNext());
        final Iterator<Annotation> iterator5 = tree.iterator(sl2.children().get(2));
        assertTrue(iterator5.hasNext());
        Annotation iterator5Annotation = iterator5.next();
        //TODO: Assert on annotation
        assertFalse(iterator5.hasNext());

    }

    @After
    public void removeTree() {
        tree = null;
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
