// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.predicate.FeatureSet;
import com.yahoo.document.predicate.SimplePredicates;
import com.yahoo.document.predicate.Predicate;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlStream;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class PredicateFieldValueTest {

    @Test
    public void requireThatFactoryProducesPredicate() {
        assertEquals(PredicateFieldValue.class, PredicateFieldValue.getFactory().create().getClass());
    }

    @Test
    public void requireThatAccessorsWork() {
        PredicateFieldValue value = new PredicateFieldValue();
        Predicate predicate = SimplePredicates.newPredicate();
        value.setPredicate(predicate);
        assertSame(predicate, value.getPredicate());
    }

    @Test
    public void requireThatConstructorsWork() {
        assertNull(new PredicateFieldValue().getPredicate());

        Predicate predicate = SimplePredicates.newPredicate();
        assertEquals(predicate, new PredicateFieldValue(predicate).getPredicate());
    }

    @Test
    public void requireThatDataTypeIsPredicate() {
        assertEquals(DataType.PREDICATE,
                     new PredicateFieldValue().getDataType());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void requireThatXmlOutputIsEmptyForNullPredicate() {
        XmlStream expected = new XmlStream();
        expected.beginTag("tag");
        expected.endTag();
        assertEquals(expected.toString(), printXml("tag", new PredicateFieldValue()));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void requireThatXmlOutputIsPredicateLanguage() {
        Predicate predicate = new FeatureSet("key", "valueA", "valueB");
        XmlStream expected = new XmlStream();
        expected.beginTag("tag");
        expected.addContent(predicate.toString());
        expected.endTag();
        assertEquals(expected.toString(), printXml("tag", new PredicateFieldValue(predicate)));
    }

    @Test
    public void requireThatClearNullsPredicate() {
        PredicateFieldValue value = new PredicateFieldValue(SimplePredicates.newPredicate());
        value.clear();
        assertNull(value.getPredicate());
    }

    @Test
    public void requireThatNullCanBeAssigned() {
        PredicateFieldValue value = new PredicateFieldValue(SimplePredicates.newPredicate());
        value.assign(null);
        assertNull(value.getPredicate());
    }

    @Test
    public void requireThatPredicateCanBeAssigned() {
        Predicate predicate = SimplePredicates.newPredicate();
        PredicateFieldValue value = new PredicateFieldValue();
        value.assign(predicate);
        assertSame(predicate, value.getPredicate());
    }

    @Test
    public void requireThatPredicateFieldValueCanBeAssigned() {
        Predicate predicate = SimplePredicates.newPredicate();
        PredicateFieldValue value = new PredicateFieldValue();
        value.assign(new PredicateFieldValue(predicate));
        assertSame(predicate, value.getPredicate());
    }

    @Test
    public void requireThatBadAssignThrows() {
        try {
            new PredicateFieldValue().assign(new Object());
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected com.yahoo.document.datatypes.PredicateFieldValue, got java.lang.Object.",
                         e.getMessage());
        }
    }

    @Test
    public void requireThatSerializeCallsBackToWriter() {
        Field field = Mockito.mock(Field.class);
        FieldWriter writer = Mockito.mock(FieldWriter.class);
        PredicateFieldValue value = new PredicateFieldValue(SimplePredicates.newPredicate());
        value.serialize(field, writer);
        Mockito.verify(writer).write(field, value);
    }

    @Test
    public void requireThatDeserializeCallsBackToReader() {
        Field field = Mockito.mock(Field.class);
        FieldReader reader = Mockito.mock(FieldReader.class);
        PredicateFieldValue value = new PredicateFieldValue(SimplePredicates.newPredicate());
        value.deserialize(field, reader);
        Mockito.verify(reader).read(field, value);
    }

    @Test
    public void requireThatWrappedValueIsPredicate() {
        Predicate predicate = SimplePredicates.newPredicate();
        PredicateFieldValue value = new PredicateFieldValue(predicate);
        assertSame(predicate, value.getWrappedValue());
    }

    @Test
    public void requireThatCloneIsImplemented() {
        PredicateFieldValue value1 = new PredicateFieldValue();
        PredicateFieldValue value2 = value1.clone();
        assertEquals(value1, value2);
        assertNotSame(value1, value2);

        value1 = new PredicateFieldValue(SimplePredicates.newString("foo"));
        value2 = value1.clone();
        assertEquals(value1, value2);
        assertNotSame(value1, value2);
        assertNotSame(value1.getPredicate(), value2.getPredicate());
    }

    @Test
    public void requireThatHashCodeIsImplemented() {
        assertEquals(new PredicateFieldValue().hashCode(),
                     new PredicateFieldValue().hashCode());
    }

    @Test
    public void requireThatEqualsIsImplemented() {
        // null predicate in lhs
        PredicateFieldValue lhs = new PredicateFieldValue();
        assertTrue(lhs.equals(lhs));
        assertFalse(lhs.equals(new Object()));

        PredicateFieldValue rhs = new PredicateFieldValue(SimplePredicates.newString("bar"));
        assertFalse(lhs.equals(rhs));
        rhs.setPredicate(null);
        assertTrue(lhs.equals(rhs));

        // non-null predicate in lhs
        lhs = new PredicateFieldValue(SimplePredicates.newString("foo"));
        assertTrue(lhs.equals(lhs));
        assertFalse(lhs.equals(new Object()));

        rhs = new PredicateFieldValue();
        assertFalse(lhs.equals(rhs));
        rhs.setPredicate(SimplePredicates.newString("bar"));
        assertFalse(lhs.equals(rhs));
        rhs.setPredicate(SimplePredicates.newString("foo"));
        assertTrue(lhs.equals(rhs));
    }

    @SuppressWarnings("deprecation")
    private static String printXml(String tag, FieldValue value) {
        XmlStream out = new XmlStream();
        out.beginTag(tag);
        if (value != null) {
            value.printXml(out);
        }
        out.endTag();
        return out.toString();
    }
}
