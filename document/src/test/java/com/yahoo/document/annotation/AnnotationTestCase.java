// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class AnnotationTestCase extends AbstractTypesTest {

    @Test
    public void testBasic() {
        AnnotationType begTagType = new AnnotationType("begin_tag");
        Annotation a = new Annotation(begTagType);
        Annotation b = new Annotation(begTagType);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotSame(a, b);

        Annotation c = new Annotation(new AnnotationType("determiner"));

        assertFalse(a.equals(c));
        assertFalse(c.equals(a));
        assertFalse(b.equals(c));
        assertFalse(c.equals(b));

        assertFalse(a.hashCode() == c.hashCode());
        assertFalse(c.hashCode() == a.hashCode());
        assertFalse(b.hashCode() == c.hashCode());
        assertFalse(c.hashCode() == b.hashCode());
    }

    @Test
    public void testFieldValues() {
        AnnotationType atype = new AnnotationType("foobar", DataType.STRING);
        StringFieldValue sfv = new StringFieldValue("balloo");

        Annotation a = new Annotation(atype);
        a.setFieldValue(sfv);
    }

    @Test
    public void testSerializeDeserialize() {
        {
            Annotation annotation = new Annotation(dummy);
            serializeAndAssert(annotation);
        }
        {
            Annotation annotation = new Annotation(number, new IntegerFieldValue(56));
            serializeAndAssert(annotation);
        }
        {
            Struct value = new Struct(person);
            value.setFieldValue("firstname", "Barack");
            value.setFieldValue("lastname", "Obama");
            value.setFieldValue("birthyear", 1909);
            Annotation annotation = new Annotation(personA, value);
            serializeAndAssert(annotation);
        }
    }

    /**
     * A test case taken from real use to verify the API ease of use
     */
    @Test
    public void testApi() {
        // Prepare
        AnnotationType type1 = new AnnotationType("type1", DataType.STRING);
        AnnotationType type2 = new AnnotationType("type2", DataType.INT);
        StringFieldValue output = new StringFieldValue("foo bar");
        SpanTree tree;

        // no shortcuts
        {
            SpanList root = new SpanList();
            tree = new SpanTree("SpanTree1", root);
            SpanNode node = new Span(0, 3);
            tree.annotate(node, new Annotation(type1, new StringFieldValue("text")));
            tree.annotate(node, new Annotation(type2, new IntegerFieldValue(1)));
            root.add(node);
            output.setSpanTree(tree);
        }

        // short
        {
            SpanList root = new SpanList();
            output.setSpanTree(new SpanTree("SpanTree2", root));
            SpanNode node = root.add(new Span(0, 3));
            node.annotate(type1, "text").annotate(type2, 1);
        }

        // shorter
        {
            SpanList root = output.setSpanTree(new SpanTree("SpanTree3")).spanList();
            root.span(0, 3).annotate(type1, "text").annotate(type2, 1);
        }

        // shortest
        {
            output.setSpanTree(new SpanTree("SpanTree4")).spanList().span(0, 3).annotate(type1, "text")
                  .annotate(type2, 1);
        }
    }

    private void serializeAndAssert(Annotation annotation) {
        GrowableByteBuffer buffer = new GrowableByteBuffer(1024);
        DocumentSerializer serializer = DocumentSerializerFactory.create6(buffer);
        serializer.write(annotation);
        buffer.flip();

        DocumentDeserializer deserializer = DocumentDeserializerFactory.create6(man, buffer);
        Annotation annotation2 = new Annotation();
        deserializer.read(annotation2);

        assertEquals(annotation, annotation2);
        assertNotSame(annotation, annotation2);
    }
}
