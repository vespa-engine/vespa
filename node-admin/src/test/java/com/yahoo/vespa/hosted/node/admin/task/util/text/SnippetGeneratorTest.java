// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.text;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author hakonhall
 */
public class SnippetGeneratorTest {
    private final SnippetGenerator generator = new SnippetGenerator();

    private void assertSnippet(String text, int maxLength, String expectedSnippet) {
        assertEquals(expectedSnippet, generator.makeSnippet(text, maxLength));
    }

    @Test
    public void prefixSnippetForReallySmallMaxLength() {
        assertSnippet(
                "This is a long text that should be snippeted", 0,
                "");

        assertSnippet(
                "This is a long text that should be snippeted", 1,
                "T");

        assertSnippet(
                "This is a long text that should be snippeted", 10,
                "This is a ");

        assertSnippet(
                "This is a long text that should be snippeted", 20,
                "This is a long text ");
    }

    @Test
    public void snippet() {
        assertSnippet(
                "This is a long text that should be snippeted", 21,
                "[...44 chars omitted]");

        assertSnippet(
                "This is a long text that should be snippeted", 22,
                "T[...43 chars omitted]");

        assertSnippet(
                "This is a long text that should be snippeted", 30,
                "This [...35 chars omitted]eted");

        assertSnippet(
                "This is a long text that should be snippeted", 31,
                "This [...34 chars omitted]peted");

        assertSnippet(
                "This is a long text that should be snippeted", 43,
                "This is a l[...22 chars omitted]e snippeted");
    }

    @Test
    public void noShorteningNeeded() {
        assertSnippet(
                "This is a long text that should be snippeted", 44,
                "This is a long text that should be snippeted");

        assertSnippet(
                "This is a long text that should be snippeted", 50,
                "This is a long text that should be snippeted");
    }
}