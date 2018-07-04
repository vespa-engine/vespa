// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DataType;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.predicate.BooleanPredicate;
import com.yahoo.document.predicate.Conjunction;
import com.yahoo.document.predicate.Disjunction;
import com.yahoo.document.predicate.FeatureRange;
import com.yahoo.document.predicate.FeatureSet;
import com.yahoo.document.predicate.Negation;
import com.yahoo.document.predicate.Predicate;
import org.junit.Test;
import java.io.IOException;

import static com.yahoo.document.serialization.SerializationTestUtils.deserializeDocument;
import static com.yahoo.document.serialization.SerializationTestUtils.serializeDocument;
import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class PredicateFieldValueSerializationTestCase {

    private final static String PREDICATE_FIELD = "my_predicate";
    private final static String PREDICATE_FILES = "src/test/resources/predicates/";
    private final static TestDocumentFactory docFactory =
            new TestDocumentFactory(createDocType(), "id:test:my_type::foo");

    private static DocumentType createDocType() {
        DocumentType type = new DocumentType("my_type");
        type.addField(PREDICATE_FIELD, DataType.PREDICATE);
        return type;
    }

    @Test
    public void requireThatPredicateFieldValuesAreDeserialized() {
        Document prevDocument = docFactory.createDocument();
        PredicateFieldValue prevPredicate = new PredicateFieldValue(new Conjunction(new FeatureSet("foo", "bar"),
                                                                                    new FeatureRange("baz", 6L, 9L)));
        prevDocument.setFieldValue(PREDICATE_FIELD, prevPredicate);
        byte[] buf = serializeDocument(prevDocument);
        Document nextDocument = deserializeDocument(buf, docFactory);
        assertEquals(prevDocument, nextDocument);
        assertEquals(prevPredicate, nextDocument.getFieldValue(PREDICATE_FIELD));
    }

    @Test
    public void requireThatPredicateDeserializationMatchesCpp() throws IOException {
        assertDeserialize("foo_in_bar_and_baz_in_cox", new Conjunction(new FeatureSet("foo", "bar"),
                                                                       new FeatureSet("baz", "cox")));
        assertDeserialize("foo_in_bar_or_baz_in_cox", new Disjunction(new FeatureSet("foo", "bar"),
                                                                      new FeatureSet("baz", "cox")));
        assertDeserialize("foo_in_6_9", new FeatureRange("foo", 6L, 9L));
        assertDeserialize("foo_in_6_x", new FeatureRange("foo", 6L, null));
        assertDeserialize("foo_in_x_9", new FeatureRange("foo", null, 9L));
        assertDeserialize("foo_in_x_x", new FeatureRange("foo", null, null));
        assertDeserialize("foo_in_x", new FeatureSet("foo"));
        assertDeserialize("foo_in_bar", new FeatureSet("foo", "bar"));
        assertDeserialize("foo_in_bar_baz", new FeatureSet("foo", "bar", "baz"));
        assertDeserialize("not_foo_in_bar", new Negation(new FeatureSet("foo", "bar")));
        assertDeserialize("true", new BooleanPredicate(true));
        assertDeserialize("false", new BooleanPredicate(false));
    }

    private static void assertDeserialize(String fileName, Predicate expected) throws IOException {
        Document document = docFactory.createDocument();
        document.setFieldValue(PREDICATE_FIELD, new PredicateFieldValue(expected));
        SerializationTestUtils.assertSerializationMatchesCpp(PREDICATE_FILES, fileName, document, docFactory);
    }
}
