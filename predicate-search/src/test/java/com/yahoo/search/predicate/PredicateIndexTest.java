// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate;

import com.yahoo.document.predicate.Predicate;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.yahoo.search.predicate.serialization.SerializationTestHelper.assertSerializationDeserializationMatches;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="mailto:magnarn@yahoo-inc.com">Magnar Nedland</a>
 * @author bjorncs
 */
public class PredicateIndexTest {

    private static final int DOC_ID = 42;

    @Test
    void requireThatPredicateIndexCanSearch() {
        PredicateIndexBuilder builder = new PredicateIndexBuilder(10);
        builder.indexDocument(1, Predicate.fromString("country in ['no', 'se'] and gender in ['male']"));
        builder.indexDocument(0x3fffffe, Predicate.fromString("country in ['no'] and gender in ['female']"));
        PredicateIndex index = builder.build();
        PredicateIndex.Searcher searcher = index.searcher();
        PredicateQuery query = new PredicateQuery();
        query.addFeature("country", "no");
        query.addFeature("gender", "male");
        assertEquals("[1]", searcher.search(query).toList().toString());
        query.addFeature("gender", "female");
        assertEquals("[1, 67108862]", searcher.search(query).toList().toString());
    }

    @Test
    void requireThatPredicateIndexCanSearchWithNotExpression() {
        {
            PredicateIndexBuilder builder = new PredicateIndexBuilder(10);
            builder.indexDocument(1, Predicate.fromString("country in ['no'] and gender not in ['male']"));
            PredicateIndex index = builder.build();
            PredicateIndex.Searcher searcher = index.searcher();
            PredicateQuery query = new PredicateQuery();
            query.addFeature("country", "no");
            query.addFeature("gender", "female");
            assertEquals("[1]", searcher.search(query).toList().toString());
        }
        {
            PredicateIndexBuilder builder = new PredicateIndexBuilder(10);
            builder.indexDocument(DOC_ID, Predicate.fromString("country in ['no'] and gender in ['male']"));
            builder.indexDocument(DOC_ID + 1, Predicate.fromString("country not in ['no']"));
            PredicateIndex index = builder.build();
            PredicateIndex.Searcher searcher = index.searcher();

            PredicateQuery query = new PredicateQuery();
            assertEquals("[43]", searcher.search(query).toList().toString());
            query.addFeature("country", "no");
            assertEquals(0, searcher.search(query).count());
        }
        {
            PredicateIndexBuilder builder = new PredicateIndexBuilder(10);
            builder.indexDocument(DOC_ID, Predicate.fromString("country not in ['no'] and gender not in ['male']"));
            PredicateIndex index = builder.build();
            PredicateIndex.Searcher searcher = index.searcher();

            PredicateQuery query = new PredicateQuery();
            assertEquals(1, searcher.search(query).count());
            query.addFeature("country", "no");
            assertEquals(0, searcher.search(query).count());
            query.addFeature("gender", "male");
            assertEquals(0, searcher.search(query).count());

            query = new PredicateQuery();
            query.addFeature("gender", "male");
            assertEquals(0, searcher.search(query).count());
        }
    }

    @Test
    void requireThatSearchesCanUseSubqueries() {
        PredicateIndexBuilder builder = new PredicateIndexBuilder(10);
        builder.indexDocument(DOC_ID, Predicate.fromString("country in [no] and gender in [male]"));
        PredicateIndex index = builder.build();
        PredicateIndex.Searcher searcher = index.searcher();

        PredicateQuery query = new PredicateQuery();
        query.addFeature("country", "no", 0x3);
        assertEquals(0, searcher.search(query).count());
        query.addFeature("gender", "male", 0x6);
        assertEquals("[[42,0x2]]", searcher.search(query).toList().toString());
    }

    @Test
    void requireThatPredicateIndexCanSearchWithRange() {
        PredicateIndexBuilder builder = new PredicateIndexBuilder(10);
        builder.indexDocument(1, Predicate.fromString("gender in ['male'] and age in [20..40]"));
        builder.indexDocument(2, Predicate.fromString("gender in ['female'] and age in [20..40]"));
        PredicateIndex index = builder.build();
        PredicateIndex.Searcher searcher = index.searcher();
        PredicateQuery query = new PredicateQuery();
        query.addFeature("gender", "male");
        query.addRangeFeature("age", 36);
        assertEquals("[1]", searcher.search(query).toList().toString());
        query.addFeature("gender", "female");
        assertEquals("[1, 2]", searcher.search(query).toList().toString());
    }

    @Test
    void requireThatPredicateIndexCanSearchWithEmptyDocuments() {
        PredicateIndexBuilder builder = new PredicateIndexBuilder(10);
        builder.indexDocument(1, Predicate.fromString("true"));
        builder.indexDocument(2, Predicate.fromString("false"));
        PredicateIndex index = builder.build();
        PredicateIndex.Searcher searcher = index.searcher();
        PredicateQuery query = new PredicateQuery();
        assertEquals("[1]", searcher.search(query).toList().toString());
    }

    @Test
    void requireThatPredicatesHavingMultipleIdenticalConjunctionsAreSupported() {
        PredicateIndexBuilder builder = new PredicateIndexBuilder(10);
        builder.indexDocument(DOC_ID, Predicate.fromString(
                "((a in ['b'] and c in ['d']) or x in ['y']) and ((a in ['b'] and c in ['d']) or z in ['w'])"));
        PredicateIndex index = builder.build();
        PredicateIndex.Searcher searcher = index.searcher();
        PredicateQuery query = new PredicateQuery();
        query.addFeature("a", "b");
        query.addFeature("c", "d");
        assertEquals("[42]", searcher.search(query).toList().toString());
    }

    @Test
    void require_that_serialization_and_deserialization_retain_data() throws IOException {
        PredicateIndexBuilder builder = new PredicateIndexBuilder(10);
        builder.indexDocument(1, Predicate.fromString("country in ['no', 'se'] and gender in ['male']"));
        builder.indexDocument(0x3fffffe, Predicate.fromString("country in ['no'] and gender in ['female']"));
        PredicateIndex index = builder.build();
        assertSerializationDeserializationMatches(
                index, PredicateIndex::writeToOutputStream, PredicateIndex::fromInputStream);
    }
}
