// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test for LanguageHacks.
 * $Id$
 */
@SuppressWarnings("deprecation")
public class LanguageHacksTestCase {

    @Test
    public void isCJK() {
        assertFalse("NULL language", LanguageHacks.isCJK(null));
        assertTrue(LanguageHacks.isCJK("zh"));
        assertFalse("Norwegian is CJK", LanguageHacks.isCJK("no"));
    }

    @Test
    public void yellDesegments() {
        assertFalse("NULL language", LanguageHacks.yellDesegments(null));
        assertTrue(LanguageHacks.yellDesegments("de"));
        assertFalse("Norwegian desegments", LanguageHacks.yellDesegments("no"));
    }
}
