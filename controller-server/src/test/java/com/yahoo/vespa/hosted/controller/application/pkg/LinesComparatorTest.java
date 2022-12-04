// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LinesComparatorTest {
    private static final String text1 = "This part of the\n" +
            "document has stayed the\n" +
            "same from version to\n" +
            "version.  It shouldn't\n" +
            "be shown if it doesn't\n" +
            "change.  Otherwise, that\n" +
            "would not be helping to\n" +
            "compress the size of the\n" +
            "changes.\n" +
            "\n" +
            "This paragraph contains\n" +
            "text that is outdated.\n" +
            "It will be deleted in the\n" +
            "near future.\n" +
            "\n" +
            "It is important to spell\n" +
            "check this dokument. On\n" +
            "the other hand, a\n" +
            "misspelled word isn't\n" +
            "the end of the world.\n" +
            "Nothing in the rest of\n" +
            "this paragraph needs to\n" +
            "be changed. Things can\n" +
            "be added after it.";
    private static final String text2 = "This is an important\n" +
            "notice! It should\n" +
            "therefore be located at\n" +
            "the beginning of this\n" +
            "document!\n" +
            "\n" +
            "This part of the\n" +
            "document has stayed the\n" +
            "same from version to\n" +
            "version.  It shouldn't\n" +
            "be shown if it doesn't\n" +
            "change.  Otherwise, that\n" +
            "would not be helping to\n" +
            "compress the size of the\n" +
            "changes.\n" +
            "\n" +
            "It is important to spell\n" +
            "check this document. On\n" +
            "the other hand, a\n" +
            "misspelled word isn't\n" +
            "the end of the world.\n" +
            "Nothing in the rest of\n" +
            "this paragraph needs to\n" +
            "be changed. Things can\n" +
            "be added after it.\n" +
            "\n" +
            "This paragraph contains\n" +
            "important new additions\n" +
            "to this document.";

    @Test
    void diff_test() {
        assertDiff(null, "", "");
        assertDiff(null, text1, text1);
        assertDiff(text1.lines().map(line -> "- " + line).collect(Collectors.joining("\n", "@@ -1,24 +1,0 @@\n", "\n")), text1, "");
        assertDiff(text1.lines().map(line -> "+ " + line).collect(Collectors.joining("\n", "@@ -1,0 +1,24 @@\n", "\n")), "", text1);
        assertDiff("@@ -1,3 +1,9 @@\n" +
                "+ This is an important\n" +
                "+ notice! It should\n" +
                "+ therefore be located at\n" +
                "+ the beginning of this\n" +
                "+ document!\n" +
                "+ \n" +
                "  This part of the\n" +
                "  document has stayed the\n" +
                "  same from version to\n" +
                "@@ -7,14 +13,9 @@\n" +
                "  would not be helping to\n" +
                "  compress the size of the\n" +
                "  changes.\n" +
                "- \n" +
                "- This paragraph contains\n" +
                "- text that is outdated.\n" +
                "- It will be deleted in the\n" +
                "- near future.\n" +
                "  \n" +
                "  It is important to spell\n" +
                "+ check this document. On\n" +
                "- check this dokument. On\n" +
                "  the other hand, a\n" +
                "  misspelled word isn't\n" +
                "  the end of the world.\n" +
                "@@ -22,3 +23,7 @@\n" +
                "  this paragraph needs to\n" +
                "  be changed. Things can\n" +
                "  be added after it.\n" +
                "+ \n" +
                "+ This paragraph contains\n" +
                "+ important new additions\n" +
                "+ to this document.\n", text1, text2);
    }

    private static void assertDiff(String expected, String left, String right) {
        assertEquals(Optional.ofNullable(expected),
                LinesComparator.diff(left.lines().collect(Collectors.toList()), right.lines().collect(Collectors.toList())));
    }
}