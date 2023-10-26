// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;

import java.util.ListIterator;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class SpanTestCase extends AbstractTypesTest {

    @Test
    public void testIteration() {
        Span span = new Span(1, 2);
        ListIterator<SpanNode> b = span.childIterator();
        assertFalse(b.hasNext());
        assertFalse(b.hasPrevious());
        assertEquals(0, b.nextIndex());
        assertEquals(0, b.previousIndex());
        try {
            b.next();
            fail();
        } catch (NoSuchElementException nsee) {
            //ok
        }
        try {
            b.previous();
            fail();
        } catch (NoSuchElementException nsee) {
            //ok
        }
        try {
            b.remove();
            fail();
        } catch (UnsupportedOperationException uoe) {
            //ok
        }
        try {
            b.set(new Span(1, 1));
            fail();
        } catch (UnsupportedOperationException uoe) {
            //ok
        }
        try {
            b.add(new Span(1, 1));
            fail();
        } catch (UnsupportedOperationException uoe) {
            //ok
        }
    }

    @Test
    public void testSerializeDeserialize() {
        {
            Span span = new Span(1, 2);
            serializeAndAssert(span);
        }
        {
            Span span = new Span(4, 15);
            serializeAndAssert(span);
        }
        {
            Span span = new Span(1, 19);
            serializeAndAssert(span);
        }
    }

    private void serializeAndAssert(Span span) {
        GrowableByteBuffer buffer;
        {
            buffer = new GrowableByteBuffer(1024);
            DocumentSerializer serializer = DocumentSerializerFactory.create6(buffer);
            StringFieldValue value = new StringFieldValue("lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lkj lk");
            SpanTree tree = new SpanTree("bababa", span);
            value.setSpanTree(tree);
            serializer.write(null, value);
            buffer.flip();
        }
        Span span2;
        {
            DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(man, buffer);
            StringFieldValue value = new StringFieldValue();
            deserializer.read(null, value);
            span2 = (Span)value.getSpanTree("bababa").getRoot();
        }

        assertEquals(span, span2);
        assertNotSame(span, span2);
    }
}
