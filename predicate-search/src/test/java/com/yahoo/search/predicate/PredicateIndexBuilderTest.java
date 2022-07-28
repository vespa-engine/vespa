// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate;

import com.yahoo.document.predicate.BooleanPredicate;
import com.yahoo.document.predicate.Predicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
public class PredicateIndexBuilderTest {

    @Test
    void requireThatIndexingMultiDocumentsWithSameIdThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            PredicateIndexBuilder builder = new PredicateIndexBuilder(2);
            builder.indexDocument(1, Predicate.fromString("a in ['b']"));
            builder.indexDocument(1, Predicate.fromString("c in ['d']"));
        });
    }

    @Test
    void requireThatEmptyDocumentsCanBeIndexed() {
        PredicateIndexBuilder builder = new PredicateIndexBuilder(10);
        assertEquals(0, builder.getZeroConstraintDocCount());
        builder.indexDocument(2, new BooleanPredicate(true));
        assertEquals(1, builder.getZeroConstraintDocCount());
        builder.build();
    }

    @Test
    void requireThatMultipleDocumentsCanBeIndexed() {
        PredicateIndexBuilder builder = new PredicateIndexBuilder(10);
        builder.indexDocument(1, Predicate.fromString("a in ['b']"));
        builder.indexDocument(2, Predicate.fromString("a in ['b']"));
        builder.indexDocument(3, Predicate.fromString("a in ['b']"));
        builder.indexDocument(4, Predicate.fromString("a in ['b']"));
        builder.indexDocument(5, Predicate.fromString("a in ['b']"));
        builder.build();
    }

}
