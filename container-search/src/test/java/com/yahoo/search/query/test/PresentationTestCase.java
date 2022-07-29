// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.test;

import com.yahoo.prelude.query.Highlight;
import com.yahoo.search.Query;
import com.yahoo.search.query.Presentation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Arne Bergene Fossaa
 */
public class PresentationTestCase {

    @Test
    void testClone() {
        Query q = new Query("");
        Presentation p = new Presentation(q);
        p.setBolding(true);
        Highlight h = new Highlight();
        h.addHighlightTerm("date", "today");
        p.setHighlight(h);
        Presentation pc = (Presentation) p.clone();
        h.addHighlightTerm("title", "Hello");
        assertTrue(pc.getBolding());
        pc.getHighlight().getHighlightItems();
        assertTrue(pc.getHighlight().getHighlightItems().containsKey("date"));
        assertFalse(pc.getHighlight().getHighlightItems().containsKey("title"));
        assertEquals(p, pc);
    }

}
