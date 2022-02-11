// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text.internal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author hakon
 */
public class SnippetGeneratorTest {
    private final SnippetGenerator generator = new SnippetGenerator();

    private void assertSnippet(String text, int sizeHint, String expectedSnippet) {
        assertEquals(expectedSnippet, generator.makeSnippet(text, sizeHint));
    }

    @Test
    public void prefixSnippetForReallySmallSizeHint() {
        assertSnippet(
                "This is a long text that should be snippeted", 0,
                "[...44 chars omitted]");

        assertSnippet(
                "This is a long text that should be snippeted", 1,
                "[...44 chars omitted]");
    }

    @Test
    public void snippet() {
        assertSnippet(
                "This is a long text that should be snippeted", 23,
                "[...44 chars omitted]");

        assertSnippet(
                "This is a long text that should be snippeted", 24,
                "T[...43 chars omitted]");

        assertSnippet(
                "This is a long text that should be snippeted", 30,
                "This[...37 chars omitted]ted");

    }

    @Test
    public void noShorteningNeeded() {
        assertSnippet(
                "This is a long text that should be snippeted", 39,
                "This is [...28 chars omitted]nippeted");

        assertSnippet(
                "This is a long text that should be snippeted", 40,
                "This is a long text that should be snippeted");

        assertSnippet(
                "This is a long text that should be snippeted", 50,
                "This is a long text that should be snippeted");
    }
}
