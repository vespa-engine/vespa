// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class SpanListTestCase extends AbstractTypesTest {

    @Test
    public void testSerializeDeserialize() {
        {
            SpanList spanList = new SpanList();
            serializeAndAssert(spanList);
        }
        {
            SpanList spanList = new SpanList();
            Span s1 = new Span(1, 2);
            Span s2 = new Span(3, 4);
            Span s3 = new Span(4, 5);
            spanList.add(s1).add(s2).add(s3);
            SpanList s4 = new SpanList();
            Span s5 = new Span(7, 8);
            Span s6 = new Span(8, 9);
            s4.add(s5).add(s6);
            spanList.add(s4);

            serializeAndAssert(spanList);
        }
    }

    private void serializeAndAssert(SpanList spanList) {
        GrowableByteBuffer buffer;
        {
            buffer = new GrowableByteBuffer(1024);
            DocumentSerializer serializer = DocumentSerializerFactory.create6(buffer);
            StringFieldValue value = new StringFieldValue("lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lk");
            SpanTree tree = new SpanTree("bababa", spanList);
            value.setSpanTree(tree);
            serializer.write(null, value);
            buffer.flip();
        }
        SpanList spanList2;
        {
            DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(man, buffer);
            StringFieldValue value = new StringFieldValue();
            deserializer.read(null, value);
            spanList2 = (SpanList)value.getSpanTree("bababa").getRoot();
        }

        assertEquals(spanList, spanList2);
        assertNotSame(spanList, spanList2);
    }

    @Test
    public void testFromAndTo() {
        Span s1 = new Span(1, 2);
        Span s2 = new Span(5, 10);

        SpanList list = new SpanList();

        assertEquals(-1, list.getFrom());
        assertEquals(-1, list.getTo());

        list.add(s1);
        assertEquals(1, list.getFrom());
        assertEquals(1, list.getFrom());
        assertEquals(3, list.getTo());
        assertEquals(3, list.getTo());

        list.add(s2);
        assertEquals(1, list.getFrom());
        assertEquals(1, list.getFrom());
        assertEquals(15, list.getTo());
        assertEquals(15, list.getTo());

        list.clearChildren();
        assertEquals(-1, list.getFrom());
        assertEquals(-1, list.getTo());

        s1 = new Span(1, 2);
        s2 = new Span(5, 10);

        list.add(s1);
        assertEquals(1, list.getFrom());
        assertEquals(1, list.getFrom());
        assertEquals(3, list.getTo());
        assertEquals(3, list.getTo());

        list.add(s2);
        assertEquals(1, list.getFrom());
        assertEquals(1, list.getFrom());
        assertEquals(15, list.getTo());
        assertEquals(15, list.getTo());

        list.remove(s1);
        assertEquals(5, list.getFrom());
        assertEquals(5, list.getFrom());
        assertEquals(15, list.getTo());
        assertEquals(15, list.getTo());

        list.remove(s2);
        assertEquals(-1, list.getFrom());
        assertEquals(-1, list.getTo());
    }

    @Test
    public void testSortRecursive() {
        SpanList root = new SpanList();
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
    }

    @Test
    public void testTwoLevelFromAndTo() {
        SpanList root = new SpanList();
        SpanList l1 = new SpanList();
        root.add(l1);

        Span s1 = new Span(0, 20);
        Span s2 = new Span(20, 20);

        l1.add(s1).add(s2);

        assertEquals(0, root.getFrom());
        assertEquals(40, root.getTo());
        assertEquals(0, l1.getFrom());
        assertEquals(40, l1.getTo());

        Span s3 = new Span(40, 20);
        l1.add(s3);

        assertEquals(0, root.getFrom());
        assertEquals(60, root.getTo());
        assertEquals(0, l1.getFrom());
        assertEquals(60, l1.getTo());
    }

    @Test
    public void testAddingToManyRoots() {
        Span s1 = new Span(1, 1);
        Span s2 = new Span(2, 1);
        Span s3 = new Span(3, 1);

        SpanList sl1 = new SpanList();
        sl1.add(s1).add(s2).add(s3);

        SpanList sl2 = new SpanList();
        try {
            sl2.add(s1).add(s2).add(s3);
            fail("Should have failed here!!");
        } catch (IllegalStateException ise) {
            //OK!
        }

        SpanTree tree = new SpanTree("foo", sl1);
        assertSame(tree, sl1.getParent());
        assertSame(sl1, tree.getRoot());

        SpanList sl3 = new SpanList();
        sl1.add(sl3);
        assertSame(sl3, sl1.children().get(3));
        assertSame(sl1, sl3.getParent());

        assertSame(tree, sl3.getSpanTree());
        assertSame(tree, sl1.getSpanTree());

        assertNull(sl2.getSpanTree());
    }

    @Test
    public void testRemoveInvalidate() {
        SpanList sl1 = new SpanList();
        Span s1 = new Span(1, 2);

        sl1.add(s1);

        SpanList sl2 = new SpanList();
        try {
            sl2.add(s1);
            fail("Should have failed.");
        } catch (IllegalStateException ise) {
            //OK!
        }

        sl1.remove(0);

        try {
            sl2.add(s1);
            fail("Should have failed.");
        } catch (IllegalStateException ise) {
            //OK!
        }
    }

    @Test
    public void testMoveSimple() {
        SpanList sl1 = new SpanList();
        SpanList sl2 = new SpanList();
        Span s1 = new Span(1, 2);

        sl1.add(s1);
        assertEquals(1, sl1.children().size());
        assertEquals(0, sl2.children().size());
        sl1.move(s1, sl2);
        assertEquals(0, sl1.children().size());
        assertEquals(1, sl2.children().size());
        sl2.move(0, sl1);
        assertEquals(1, sl1.children().size());
        assertEquals(0, sl2.children().size());
    }

    @Test
    public void testMoveAlternate() {
        AlternateSpanList asl1 = new AlternateSpanList();
        AlternateSpanList asl2 = new AlternateSpanList();
        Span s1 = new Span(1, 2);

        asl1.add(s1);
        assertEquals(1, asl1.children().size());
        assertEquals(0, asl2.children().size());
        asl1.move(s1, asl2);
        assertEquals(0, asl1.children().size());
        assertEquals(1, asl2.children().size());
        asl2.move(0, asl1);
        assertEquals(1, asl1.children().size());
        assertEquals(0, asl2.children().size());
    }

    @Test
    public void testMoveAlternateAdvances() {
        AlternateSpanList asl1 = new AlternateSpanList();
        AlternateSpanList asl2 = new AlternateSpanList();
        Span s1 = new Span(1, 2);

        asl1.addChildren(new ArrayList<SpanNode>(), 50d);
        asl1.addChildren(new ArrayList<SpanNode>(), 50d);
        asl2.addChildren(new ArrayList<SpanNode>(), 50d);
        asl2.addChildren(new ArrayList<SpanNode>(), 50d);

        asl1.add(s1);
        assertEquals(1, asl1.children(0).size());
        assertEquals(0, asl1.children(1).size());
        assertEquals(0, asl1.children(2).size());
        assertEquals(0, asl2.children(0).size());
        assertEquals(0, asl2.children(1).size());
        assertEquals(0, asl2.children(2).size());
        asl1.move(s1, asl2, 2);
        assertEquals(0, asl1.children(0).size());
        assertEquals(0, asl1.children(1).size());
        assertEquals(0, asl1.children(2).size());
        assertEquals(0, asl2.children(0).size());
        assertEquals(0, asl2.children(1).size());
        assertEquals(1, asl2.children(2).size());
        asl2.move(2, 0, asl1, 1);
        assertEquals(0, asl1.children(0).size());
        assertEquals(1, asl1.children(1).size());
        assertEquals(0, asl1.children(2).size());
        assertEquals(0, asl2.children(0).size());
        assertEquals(0, asl2.children(1).size());
        assertEquals(0, asl2.children(2).size());
    }

    @Test
    public void testGetStringFieldValue() {
        StringFieldValue text = getAnnotatedString();
        {
            SpanTree tree = text.getSpanTree("ballooo");
            assertSame(text, tree.getStringFieldValue());

            AlternateSpanList root = (AlternateSpanList)tree.getRoot();
            Iterator<SpanNode> it = root.childIteratorRecursive();

            while (it.hasNext()) {
                assertSame(text, it.next().getStringFieldValue());
            }
        }
        {
            SpanTree tree = text.getSpanTree("fruits");
            assertSame(text, tree.getStringFieldValue());

            SpanList root = (SpanList)tree.getRoot();
            Iterator<SpanNode> it = root.childIteratorRecursive();

            while (it.hasNext()) {
                assertSame(text, it.next().getStringFieldValue());
            }
        }
        {
            SpanTree tree = text.removeSpanTree("ballooo");
            assertNull(tree.getStringFieldValue());

            AlternateSpanList root = (AlternateSpanList)tree.getRoot();
            Iterator<SpanNode> it = root.childIteratorRecursive();

            while (it.hasNext()) {
                assertNull(it.next().getStringFieldValue());
            }
        }
        {
            SpanTree tree = text.removeSpanTree("fruits");
            assertNull(tree.getStringFieldValue());

            SpanList root = (SpanList)tree.getRoot();
            Iterator<SpanNode> it = root.childIteratorRecursive();

            while (it.hasNext()) {
                assertNull(it.next().getStringFieldValue());
            }
        }
    }
}
