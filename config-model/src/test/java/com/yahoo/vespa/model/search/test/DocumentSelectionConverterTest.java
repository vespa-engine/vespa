// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import com.yahoo.document.select.parser.ParseException;
import com.yahoo.vespa.model.search.DocumentSelectionConverter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for RemoveSelection.
 * @author Ulf Lilleengen
 */
public class DocumentSelectionConverterTest {
    @Test
    public void testQueryConversion() throws ParseException, IllegalArgumentException, UnsupportedOperationException {
        DocumentSelectionConverter converter = new DocumentSelectionConverter("music.expire>now() - 3600 and video.expire > now() - 300");
        assertEquals("expire:>now(3600)", converter.getQuery("music"));
        assertEquals("expire:<now(3600)", converter.getInvertedQuery("music"));
        assertEquals("expire:>now(300)", converter.getQuery("video"));
        assertEquals("expire:<now(300)", converter.getInvertedQuery("video"));
        assertTrue(null == converter.getQuery("book"));
        assertTrue(null == converter.getInvertedQuery("book"));
    }
    @Test
    public void testSelection() throws ParseException, IllegalArgumentException, UnsupportedOperationException {
        DocumentSelectionConverter converter = new DocumentSelectionConverter("music.expire>music.expire.nowdate");
        assertTrue(converter.getQuery("music") == null);
        assertTrue(converter.getInvertedQuery("music") == null);
    }
}
