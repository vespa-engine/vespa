// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class AlternateSpanListTestCase extends AbstractTypesTest {

    @Test
    public void testSerializeDeserialize() {
        {
            AlternateSpanList alternateSpanList = new AlternateSpanList();
            serializeAndAssert(alternateSpanList);
        }
        {
            AlternateSpanList alternateSpanList = new AlternateSpanList();
            Span s1 = new Span(1, 2);
            Span s2 = new Span(3, 4);
            Span s3 = new Span(4, 5);
            alternateSpanList.add(s1).add(s2).add(s3);
            AlternateSpanList s4 = new AlternateSpanList();
            Span s5 = new Span(7, 8);
            Span s6 = new Span(8, 9);
            s4.add(s5).add(s6);
            alternateSpanList.add(s4);

            List<SpanNode> alternateChildren = new ArrayList<>();
            Span s7 = new Span(1, 4);
            Span s8 = new Span(1, 9);
            Span s9 = new Span(5, 10);
            alternateChildren.add(s7);
            alternateChildren.add(s8);
            alternateChildren.add(s9);

            alternateSpanList.addChildren(alternateChildren, 5.55);

            serializeAndAssert(alternateSpanList);
        }
    }

    private void serializeAndAssert(AlternateSpanList alternateSpanList) {
        GrowableByteBuffer buffer;
        {
            buffer = new GrowableByteBuffer(1024);
            DocumentSerializer serializer = DocumentSerializerFactory.create6(buffer);
            StringFieldValue value = new StringFieldValue("lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lk");
            SpanTree tree = new SpanTree("bababa", alternateSpanList);
            value.setSpanTree(tree);
            serializer.write(null, value);
            buffer.flip();
        }
        AlternateSpanList alternateSpanList2;
        {
            DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(man, buffer);
            StringFieldValue value = new StringFieldValue();
            deserializer.read(null, value);
            alternateSpanList2 = (AlternateSpanList)value.getSpanTree("bababa").getRoot();
        }

        assertEquals(alternateSpanList, alternateSpanList2);
        assertNotSame(alternateSpanList, alternateSpanList2);
    }

    @Test
    public void testToString() {
        SpanList root = new SpanList();
        AlternateSpanList branch = new AlternateSpanList();

        SpanNode sn1 = new Span(0, 5);
        SpanNode span1 = new Span(0, 3);
        root.add(sn1);
        branch.add(span1);
        root.add(branch);

        SpanNode span11 = new Span(0, 3);
        SpanNode span22 = new Span(1, 9);
        SpanNode span33 = new Span(12, 10);

        SpanList alternate = new SpanList();
        alternate.add(span11);
        alternate.add(span22);
        alternate.add(span33);

        List<SpanNode> subtreeList = new ArrayList<>();
        subtreeList.add(alternate);

        branch.setProbability(0, 100.0d);
        branch.addChildren(subtreeList, 50.0d);

        assertNotNull(root.toString());
        assertNotNull(branch.toString());
    }

    @Test
    public void testSortRecursive() {
        AlternateSpanList root = new AlternateSpanList();
        Span a1 = new Span(0, 1);
        SpanList b1 = new SpanList();
        SpanList c1 = new SpanList();
        Span d1 = new Span(9, 1);
        root.add(d1).add(c1).add(a1).add(b1);

        Span aB2 = new Span(1, 1);
        Span bB2 = new Span(2, 1);
        Span cB2 = new Span(3, 1);
        Span dB2 = new Span(4, 1);
        b1.add(dB2).add(cB2).add(bB2).add(aB2);

        Span aC2 = new Span(5, 1);
        Span bC2 = new Span(6, 1);
        Span cC2 = new Span(7, 1);
        Span dC2 = new Span(8, 1);
        c1.add(cC2).add(aC2).add(bC2).add(dC2);

        Span altA1 = new Span(0, 1);
        Span altB1 = new Span(1, 1);
        List<SpanNode> altList = new ArrayList<>(2);
        altList.add(altB1);
        altList.add(altA1);
        root.addChildren(1, altList, 0.5);

        root.sortChildrenRecursive();
        assertSame(a1, root.children().get(0));
        assertSame(b1, root.children().get(1));
        assertSame(c1, root.children().get(2));
        assertSame(d1, root.children().get(3));
        assertSame(aB2, b1.children().get(0));
        assertSame(bB2, b1.children().get(1));
        assertSame(cB2, b1.children().get(2));
        assertSame(dB2, b1.children().get(3));
        assertSame(aC2, c1.children().get(0));
        assertSame(bC2, c1.children().get(1));
        assertSame(cC2, c1.children().get(2));
        assertSame(dC2, c1.children().get(3));

        assertSame(altA1, root.children(1).get(0));
        assertSame(altB1, root.children(1).get(1));
    }

    @Test
    public void testIterator() {
        AlternateSpanList asl1 = new AlternateSpanList();

        Span span10 = new Span(1, 2);
        Span span20 = new Span(2, 3);
        Span span30 = new Span(3, 4);
        List<SpanNode> subTree0 = new ArrayList<>(3);
        subTree0.add(span10);
        subTree0.add(span20);
        subTree0.add(span30);

        Span span11 = new Span(1, 2);
        Span span21 = new Span(2, 3);
        Span span31 = new Span(3, 4);
        List<SpanNode> subTree1 = new ArrayList<>(3);
        subTree1.add(span11);
        subTree1.add(span21);
        subTree1.add(span31);

        Span span12 = new Span(1, 2);
        Span span22 = new Span(2, 3);
        Span span32 = new Span(3, 4);
        List<SpanNode> subTree2 = new ArrayList<>(3);
        subTree2.add(span12);
        subTree2.add(span22);
        subTree2.add(span32);

        asl1.addChildren(0, subTree0, 0.1);
        asl1.addChildren(1, subTree1, 0.1);
        asl1.addChildren(2, subTree2, 0.1);

        ListIterator<SpanNode> it = asl1.childIterator();
        assertSame(span10, it.next());
        assertSame(span20, it.next());
        assertSame(span30, it.next());
        assertSame(span11, it.next());
        assertSame(span21, it.next());
        assertSame(span31, it.next());
        assertSame(span12, it.next());
        assertSame(span22, it.next());
        assertSame(span32, it.next());
        assertFalse(it.hasNext());
    }

    @Test
    public void testIteratorRecursive() {
        AlternateSpanList asl1 = new AlternateSpanList();

        Span span10 = new Span(1, 1);
        Span span20 = new Span(2, 1);
        Span span30 = new Span(3, 1);
        List<SpanNode> subTree0 = new ArrayList<>(3);
        subTree0.add(span10);
        subTree0.add(span20);
        subTree0.add(span30);

        Span span11 = new Span(4, 1);
        Span span21 = new Span(5, 1);
        Span span31 = new Span(6, 1);
        SpanList sl112 = new SpanList();
        sl112.add(span11);
        sl112.add(span21);
        sl112.add(span31);
        SpanList sl11 = new SpanList();
        sl11.add(sl112);
        List<SpanNode> subTree1 = new ArrayList<>(1);
        subTree1.add(sl11);

        Span span12 = new Span(7, 1);
        Span span22 = new Span(8, 1);
        Span span32 = new Span(9, 1);
        List<SpanNode> subTree2 = new ArrayList<>(3);
        subTree2.add(span12);
        subTree2.add(span22);
        subTree2.add(span32);

        asl1.addChildren(0, subTree0, 0.1);
        asl1.addChildren(1, subTree1, 0.1);
        asl1.addChildren(2, subTree2, 0.1);

        ListIterator<SpanNode> it = asl1.childIteratorRecursive();
        assertSame(span10, it.next());
        assertSame(span20, it.next());
        assertSame(span30, it.next());
        assertSame(span11, it.next());
        assertSame(span21, it.next());
        assertSame(span31, it.next());
        assertSame(sl112, it.next());
        assertSame(sl11, it.next());
        assertSame(span12, it.next());
        assertSame(span22, it.next());
        assertSame(span32, it.next());
        assertFalse(it.hasNext());
    }
}
