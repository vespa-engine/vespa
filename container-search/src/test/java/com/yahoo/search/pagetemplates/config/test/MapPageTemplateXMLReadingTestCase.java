// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.config.test;

import com.yahoo.search.pagetemplates.PageTemplate;
import com.yahoo.search.pagetemplates.PageTemplateRegistry;
import com.yahoo.search.pagetemplates.config.PageTemplateXMLReader;
import com.yahoo.search.pagetemplates.model.MapChoice;
import com.yahoo.search.pagetemplates.model.Placeholder;
import com.yahoo.search.pagetemplates.model.Section;
import com.yahoo.search.pagetemplates.model.Source;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class MapPageTemplateXMLReadingTestCase {

    private String root="src/test/java/com/yahoo/search/pagetemplates/config/test/examples/mapexamples/";

    @Test
    public void testMap1() {
        PageTemplateRegistry registry=new PageTemplateXMLReader().read(root);
        assertCorrectMap1(registry.getComponent("map1"));
    }

    private void assertCorrectMap1(PageTemplate page) {
        assertNotNull("map1 was read",page);
        Section root=page.getSection();
        assertTrue(((Section)((Section)root.elements(Section.class).get(0)).elements(Section.class).get(0)).elements().get(0) instanceof Placeholder);
        assertTrue(((Section)((Section)root.elements(Section.class).get(0)).elements(Section.class).get(1)).elements().get(0) instanceof Placeholder);
        assertTrue(((Section)((Section)root.elements(Section.class).get(1)).elements(Section.class).get(0)).elements().get(0) instanceof Placeholder);
        assertTrue(((Section)((Section)root.elements(Section.class).get(1)).elements(Section.class).get(1)).elements().get(0) instanceof Placeholder);
        assertEquals("box1source",((Placeholder) ((Section)((Section)root.elements(Section.class).get(0)).elements(Section.class).get(0)).elements().get(0)).getId());
        assertEquals("box2source",((Placeholder) ((Section)((Section)root.elements(Section.class).get(0)).elements(Section.class).get(1)).elements().get(0)).getId());
        assertEquals("box3source",((Placeholder) ((Section)((Section)root.elements(Section.class).get(1)).elements(Section.class).get(0)).elements().get(0)).getId());
        assertEquals("box4source",((Placeholder) ((Section)((Section)root.elements(Section.class).get(1)).elements(Section.class).get(1)).elements().get(0)).getId());

        MapChoice map=(MapChoice)root.elements().get(2);
        assertEquals("box1source",map.placeholderIds().get(0));
        assertEquals("box2source",map.placeholderIds().get(1));
        assertEquals("box3source",map.placeholderIds().get(2));
        assertEquals("box4source",map.placeholderIds().get(3));
        assertEquals("source1",((Source)((List<?>)map.values().get(0)).get(0)).getName());
        assertEquals("source2",((Source)((List<?>)map.values().get(1)).get(0)).getName());
        assertEquals("source3",((Source)((List<?>)map.values().get(2)).get(0)).getName());
        assertEquals("source4",((Source)((List<?>)map.values().get(3)).get(0)).getName());

        PageTemplateXMLReadingTestCase.assertCorrectSources("source1 source2 source3 source4",page);
    }

}
