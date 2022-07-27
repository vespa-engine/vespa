// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.predicate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class PredicateOperatorTest {

    @Test
    void requireThatOperatorIsAPredicate() {
        assertTrue(Predicate.class.isAssignableFrom(PredicateOperator.class));
    }
}
