// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.language.process.Embedder;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests ignored selection.
 * Selection of components from a map is tested in tests of the expressions using this mechanism (EmbedExpression etc.).
 *
 * @author bratseth
 */
public class ComponentsTestCase {

    /**
     * Components.Ignored must satisfy these three conditions which ensures there will be no exceptions
     * during expression construction in environments that doesn't construct components.
     */
    @Test
    public void testIgnored() {
        var ignored = new Components.Ignored<>(Embedder.FailingEmbedder.factory());

        // As these conditions are satisfied
        assertFalse(ignored.isEmpty());
        assertFalse(ignored.singleSelected().isEmpty());
        assertTrue(ignored.get("any id") instanceof Embedder.FailingEmbedder);

        // ... this does not cause an exception
        new Components.Selected<>("embedder",
                                  new Components.Ignored<>(Embedder.FailingEmbedder.factory()),
                                  "any id",
                                  true,
                                  List.of());
    }

}
