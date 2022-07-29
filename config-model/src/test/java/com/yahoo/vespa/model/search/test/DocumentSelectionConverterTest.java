// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import com.yahoo.document.select.parser.ParseException;
import com.yahoo.vespa.model.search.DocumentSelectionConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for RemoveSelection.
 * @author Ulf Lilleengen
 */
public class DocumentSelectionConverterTest {
    @Test
    void testQueryConversion() throws ParseException, IllegalArgumentException, UnsupportedOperationException {
        DocumentSelectionConverter converter = new DocumentSelectionConverter("music.expire>now() - 3600 and video.expire > now() - 300");
        assertEquals("expire:>now(3600)", converter.getQuery("music"));
        assertEquals("expire:<now(3600)", converter.getInvertedQuery("music"));
        assertEquals("expire:>now(300)", converter.getQuery("video"));
        assertEquals("expire:<now(300)", converter.getInvertedQuery("video"));
        assertNull(converter.getQuery("book"));
        assertNull(converter.getInvertedQuery("book"));
    }

    @Test
    void testSelection() throws ParseException, IllegalArgumentException, UnsupportedOperationException {
        DocumentSelectionConverter converter = new DocumentSelectionConverter("music.expire>music.expire.nowdate");
        assertNull(converter.getQuery("music"));
        assertNull(converter.getInvertedQuery("music"));
    }
}
