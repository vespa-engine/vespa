// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.config.test;

import com.yahoo.search.pagetemplates.PageTemplate;
import com.yahoo.search.pagetemplates.PageTemplateRegistry;
import com.yahoo.search.pagetemplates.PageTemplatesConfig;
import com.yahoo.search.pagetemplates.config.PageTemplateConfigurer;
import com.yahoo.search.pagetemplates.config.PageTemplateXMLReader;
import com.yahoo.search.pagetemplates.engine.Resolution;
import com.yahoo.search.pagetemplates.engine.Resolver;
import com.yahoo.search.pagetemplates.engine.resolvers.DeterministicResolver;
import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.pagetemplates.model.Renderer;
import com.yahoo.search.pagetemplates.model.Section;
import com.yahoo.search.pagetemplates.model.Source;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class PageTemplateXMLReadingTestCase {

    private String root="src/test/java/com/yahoo/search/pagetemplates/config/test/";

    @Test
    public void testExamples() {
        PageTemplateRegistry registry=new PageTemplateXMLReader().read(root + "examples");
        assertCorrectSerp(registry.getComponent("serp"));
        assertCorrectSlottingSerp(registry.getComponent("slottingSerp"));
        assertCorrectRichSerp(registry.getComponent("richSerp"));
        assertCorrectRicherSerp(registry.getComponent("richerSerp"));
        assertCorrectIncluder(registry.getComponent("includer"));
        assertCorrectGeneric(registry.getComponent("generic"));
    }

    @Test
    public void testConfigReading() {
        PageTemplatesConfig config = new PageTemplatesConfig(new PageTemplatesConfig.Builder()
                .page("<page id=\"slottingSerp\" layout=\"mainAndRight\">\n    <section layout=\"column\" region=\"main\" source=\"*\" order=\"-[rank]\"/>\n    <section layout=\"column\" region=\"right\" source=\"ads\"/>\n</page>\n")
                .page("<page id=\"richSerp\" layout=\"mainAndRight\">\n    <section layout=\"row\" region=\"main\">\n        <section layout=\"column\" description=\"left main pane\">\n            <section layout=\"row\" max=\"5\" description=\"Bar of images, from one of two possible sources\">\n                <choice method=\"annealing\">\n                    <source name=\"images\"/>\n                    <source name=\"flickr\"/>\n                </choice>\n            </section>\n            <section max=\"1\" source=\"local map video ticker weather\" description=\"A single relevant graphically rich element\"/>\n            <section order=\"-[rank]\" max=\"10\" source=\"web news\" description=\"Various kinds of traditional search results\"/>\n        </section>\n        <section layout=\"column\" order=\"[source]\" source=\"answers blogs twitter\" description=\"right main pane, ugc stuff, grouped by source\"/>\n    </section>\n    <section layout=\"column\" source=\"ads\" region=\"right\"/>\n</page>\n")
                .page("<page id=\"footer\">\n    <section layout=\"row\" source=\"popularSearches\"/>\n    <section id=\"extraFooter\" layout=\"row\" source=\"topArticles\"/>\n</page>\n")
                .page("<page id=\"richerSerp\" layout=\"column\">\n    <include idref=\"header\"/>\n    <section layout=\"mainAndRight\">\n        <section layout=\"row\" region=\"main\">\n            <section layout=\"column\" description=\"left main pane\">\n                <choice>\n                    <alternative>\n                        <section layout=\"row\" max=\"5\" description=\"Bar of images, from one of two possible sources\">\n                            <choice>\n                                <source name=\"images\"/>\n                                <alternative>\n                                    <source name=\"flickr\">\n                                        <renderer name=\"mouseOverImage\"/>\n                                    </source>\n                                    <source name=\"twitpic\">\n                                        <choice>\n                                            <renderer name=\"mouseOverImage\">\n                                                <parameter name=\"hovertime\">5</parameter>\n                                                <parameter name=\"borderColor\">#ff00ff</parameter>\n                                            </renderer>\n                                            <renderer name=\"regularImage\"/>\n                                        </choice>\n                                        <parameter name=\"filter\">origin=twitter</parameter>\n                                    </source>\n                                </alternative>\n                            </choice>\n                            <choice>\n                                <renderer name=\"regularImageBox\"/>\n                                <renderer name=\"newImageBox\"/>\n                            </choice>\n                        </section>\n                        <section max=\"1\" source=\"local map video ticker weather\" description=\"A single relevant graphically rich element\"/>\n                    </alternative>\n                    <section order=\"[source]\" max=\"10\" source=\"web news\" description=\"Various kinds of traditional search results\"/>\n                </choice>\n            </section>\n            <section layout=\"column\" order=\"[source]\" source=\"answers blogs twitter\" description=\"right main pane, ugc stuff, grouped by source\"/>\n        </section>\n        <section layout=\"column\" source=\"ads\" region=\"right\" order=\"-[rank] clickProbability\">\n            <renderer name=\"newAdBox\"/>\n        </section>\n    </section>\n    <include idref=\"footer\"/>\n</page>\n")
                .page("<page id=\"header\">\n    <section layout=\"row\">\n        <section source=\"global\"/>\n        <section source=\"notifications\"/>\n    </section>\n</page>\n")
        );
        PageTemplateRegistry registry = PageTemplateConfigurer.toRegistry(config);
        assertCorrectSlottingSerp(registry.getComponent("slottingSerp"));
        assertCorrectRichSerp(registry.getComponent("richSerp"));
        assertCorrectRicherSerp(registry.getComponent("richerSerp"));
    }

    @Test
    public void testInvalidFilename() {
        try {
            PageTemplateRegistry registry=new PageTemplateXMLReader().read(root + "examples/invalidfilename");
            assertEquals(0,registry.allComponents().size());
            fail("Should have caused an exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("The file name of page template 'notinvalid' must be 'notinvalid.xml' but was 'invalid.xml'",e.getMessage());
        }
    }

    protected void assertCorrectSerp(PageTemplate page) {
        assertNotNull("'serp' was read",page);
        Section rootSection=page.getSection();
        assertNotNull(rootSection);
        assertEquals("mainAndRight",rootSection.getLayout().getName());
        Section main=(Section)rootSection.elements(Section.class).get(0);
        assertEquals("column",main.getLayout().getName());
        assertEquals("main",main.getRegion());
        assertEquals("web",((Source)main.elements(Source.class).get(0)).getName());
        Section right=(Section)rootSection.elements(Section.class).get(1);
        assertEquals("column",right.getLayout().getName());
        assertEquals("right",right.getRegion());
        assertEquals("ads",((Source)right.elements(Source.class).get(0)).getName());
    }

    protected void assertCorrectSlottingSerp(PageTemplate page) {
        assertNotNull("'slotting serp' was read",page);
        Section rootSection=page.getSection();
        Section main=(Section)rootSection.elements(Section.class).get(0);
        assertEquals("-[rank]",main.getOrder().toString());
        assertEquals(Source.any,main.elements(Source.class).get(0));

        assertCorrectSources("* ads",page);
    }

    protected void assertCorrectRichSerp(PageTemplate page) {
        assertNotNull("'rich serp' was read",page);
        Section rootSection=page.getSection();
        assertNotNull(rootSection);
        assertEquals("mainAndRight",rootSection.getLayout().getName());

        Section main=(Section)rootSection.elements(Section.class).get(0);
        assertEquals("row",main.getLayout().getName());
        assertEquals("main",main.getRegion());
        Section leftMain=(Section)main.elements(Section.class).get(0);
        assertEquals("column",leftMain.getLayout().getName());
        Section imageBar=(Section)leftMain.elements(Section.class).get(0);
        assertEquals("row",imageBar.getLayout().getName());
        assertEquals(5,imageBar.getMax());
        assertEquals("annealing",((Choice)imageBar.elements(Source.class).get(0)).getMethod().toString());
        assertEquals("images",((Source)((Choice)imageBar.elements(Source.class).get(0)).alternatives().get(0).get(0)).getName());
        assertEquals("flickr",((Source)((Choice)imageBar.elements(Source.class).get(0)).alternatives().get(1).get(0)).getName());
        Section richElement=(Section)leftMain.elements(Section.class).get(1);
        assertEquals(1,richElement.getMax());
        assertEquals("[source 'local', source 'map', source 'video', source 'ticker', source 'weather']",richElement.elements(Source.class).toString());
        Section webResults=(Section)leftMain.elements(Section.class).get(2);
        assertEquals("-[rank]",webResults.getOrder().toString());
        assertEquals(10,webResults.getMax());
        assertEquals("[source 'web', source 'news']",webResults.elements(Source.class).toString());
        Section rightMain=(Section)main.elements(Section.class).get(1);
        assertEquals("column",rightMain.getLayout().getName());
        assertEquals("+[source]",rightMain.getOrder().toString());
        assertEquals("[source 'answers', source 'blogs', source 'twitter']",rightMain.elements(Source.class).toString());

        Section right=(Section)rootSection.elements(Section.class).get(1);
        assertEquals("column",right.getLayout().getName());
        assertEquals("right",right.getRegion());
        assertEquals("ads",((Source)right.elements(Source.class).get(0)).getName());
    }

    protected void assertCorrectRicherSerp(PageTemplate page) {
        assertNotNull("'richer serp' was read",page);

        // Check resolution as we go
        Resolver resolver=new DeterministicResolver();
        Resolution resolution=resolver.resolve(Choice.createSingleton(page),null,null);

        Section root=page.getSection();
        assertNotNull(root);
        assertEquals("column",root.getLayout().getName());

        assertEquals("Sections was correctly imported and combined with the section in this",4,root.elements(Section.class).size());

        assertCorrectHeader((Section)root.elements(Section.class).get(0));

        Section body=(Section)root.elements(Section.class).get(1);
        assertEquals("mainAndRight",body.getLayout().getName());

        Section main=(Section)body.elements(Section.class).get(0);
        assertEquals("row",main.getLayout().getName());
        assertEquals("main",main.getRegion());

        Section leftMain=(Section)main.elements(Section.class).get(0);
        assertEquals("column",leftMain.getLayout().getName());
        assertEquals(1,resolution.getResolution((Choice)leftMain.elements(Section.class).get(0)));

        Section imageBar=(Section)((Choice)leftMain.elements(Section.class).get(0)).alternatives().get(0).get(0);
        assertEquals("row",imageBar.getLayout().getName());
        assertEquals(5,imageBar.getMax());
        assertEquals(2,((Choice)imageBar.elements(Source.class).get(0)).alternatives().size());
        assertEquals("images",((Source)((Choice)imageBar.elements(Source.class).get(0)).alternatives().get(0).get(0)).getName());
        assertEquals(1,resolution.getResolution((Choice)imageBar.elements(Source.class).get(0)));
        assertEquals(1,resolution.getResolution((Choice)imageBar.elements(Renderer.class).get(0)));

        Source flickrSource=(Source)((Choice)imageBar.elements(Source.class).get(0)).alternatives().get(1).get(0);
        assertEquals("flickr",flickrSource.getName());
        assertEquals(1,flickrSource.renderers().size());
        assertEquals("mouseOverImage",((Renderer)flickrSource.renderers().get(0)).getName());

        Source twitpicSource=(Source)((Choice)imageBar.elements(Source.class).get(0)).alternatives().get(1).get(1);
        assertEquals("twitpic",twitpicSource.getName());
        assertEquals(1,twitpicSource.parameters().size());
        assertEquals("origin=twitter",twitpicSource.parameters().get("filter"));
        assertEquals(2,((Choice)twitpicSource.renderers().get(0)).alternatives().size());
        assertEquals(1,resolution.getResolution((Choice)twitpicSource.renderers().get(0)));

        Renderer mouseOverImageRenderer=(Renderer)((Choice)twitpicSource.renderers().get(0)).alternatives().get(0).get(0);
        assertEquals("mouseOverImage", mouseOverImageRenderer.getName());
        assertEquals(2, mouseOverImageRenderer.parameters().size());
        assertEquals("5", mouseOverImageRenderer.parameters().get("hovertime"));
        assertEquals("#ff00ff", mouseOverImageRenderer.parameters().get("borderColor"));
        assertEquals("regularImage",((Renderer)((Choice)twitpicSource.renderers().get(0)).alternatives().get(1).get(0)).getName());
        assertEquals(2,((Choice)imageBar.elements(Renderer.class).get(0)).alternatives().size());
        assertEquals("regularImageBox",((Renderer)((Choice)imageBar.elements(Renderer.class).get(0)).alternatives().get(0).get(0)).getName());
        assertEquals("newImageBox",((Renderer)((Choice)imageBar.elements(Renderer.class).get(0)).alternatives().get(1).get(0)).getName());

        Section richElement=(Section)((Choice)leftMain.elements(Section.class).get(0)).get(0).get(1);
        assertEquals(1,richElement.getMax());
        assertEquals("[source 'local', source 'map', source 'video', source 'ticker', source 'weather']",richElement.elements(Source.class).toString());

        Section webResults=(Section)((Choice)leftMain.elements(Section.class).get(0)).get(1).get(0);
        assertEquals("+[source]",webResults.getOrder().toString());
        assertEquals(10,webResults.getMax());
        assertEquals("[source 'web', source 'news']",webResults.elements(Source.class).toString());

        Section rightMain=(Section)main.elements(Section.class).get(1);
        assertEquals("column",rightMain.getLayout().getName());
        assertEquals("+[source]",rightMain.getOrder().toString());
        assertEquals("[source 'answers', source 'blogs', source 'twitter']",rightMain.elements(Source.class).toString());

        Section right=(Section)body.elements(Section.class).get(1);
        assertEquals("column",right.getLayout().getName());
        assertEquals("right",right.getRegion());
        assertEquals("ads",((Source)right.elements(Source.class).get(0)).getName());
        assertEquals("newAdBox",((Renderer)right.elements(Renderer.class).get(0)).getName());
        assertEquals("-[rank] +clickProbability",right.getOrder().toString());

        assertCorrectFooter((Section)root.elements(Section.class).get(2));
        assertEquals("extraFooter",((Section)root.elements(Section.class).get(3)).getId());

        // Check getSources()
        assertCorrectSources("ads answers blogs flickr global images local map news notifications " +
                             "popularSearches ticker topArticles twitpic twitter video weather web",page);
    }

    static void assertCorrectSources(String expectedSourceNameString,PageTemplate page) {
        String[] expectedSourceNames=expectedSourceNameString.split(" ");
        Set<String> sourceNames=new HashSet<>();
        for (Source source : page.getSources())
            sourceNames.add(source.getName());
        assertEquals("Expected " + expectedSourceNames.length + " elements in " + sourceNames,
                     expectedSourceNames.length,sourceNames.size());
        for (String expectedSourceName : expectedSourceNames)
            assertTrue("Sources did not include '" + expectedSourceName+ "'",sourceNames.contains(expectedSourceName));
    }

    protected void assertCorrectIncluder(PageTemplate page) {
        assertNotNull("'includer' was read",page);

        Resolution resolution=new DeterministicResolver().resolve(Choice.createSingleton(page),null,null);

        Section case1=(Section)page.getSection().elements(Section.class).get(0);
        assertCorrectHeader((Section)case1.elements(Section.class).get(0));
        assertCorrectFooter((Section)case1.elements(Section.class).get(1));

        Section case2=(Section)page.getSection().elements(Section.class).get(1);
        assertCorrectHeader((Section)((Choice)case2.elements(Section.class).get(0)).get(0).get(0));
        assertCorrectFooter((Section)((Choice)case2.elements(Section.class).get(0)).get(1).get(0));
        assertEquals(1,resolution.getResolution((Choice)case2.elements(Section.class).get(0)));

        Section case3=(Section)page.getSection().elements(Section.class).get(2);
        assertCorrectHeader((Section)((Choice)case3.elements(Section.class).get(0)).get(0).get(0));
        assertCorrectFooter((Section)((Choice)case3.elements(Section.class).get(0)).get(1).get(0));
        assertEquals(1,resolution.getResolution((Choice)case3.elements(Section.class).get(0)));

        Section case4=(Section)page.getSection().elements(Section.class).get(3);
        assertEquals("first",((Section)((Choice)case4.elements(Section.class).get(0)).get(0).get(0)).getId());
        assertCorrectHeader((Section)((Choice)case4.elements(Section.class).get(0)).get(1).get(0));
        assertEquals("middle",((Section)((Choice)case4.elements(Section.class).get(0)).get(2).get(0)).getId());
        assertCorrectFooter((Section)((Choice)case4.elements(Section.class).get(0)).get(3).get(0));
        assertEquals("last",((Section)((Choice)case4.elements(Section.class).get(0)).get(4).get(0)).getId());
        assertEquals(4,resolution.getResolution((Choice)case4.elements(Section.class).get(0)));

        Section case5=(Section)page.getSection().elements(Section.class).get(4);
        assertEquals(2,((Choice)case5.elements(Section.class).get(0)).alternatives().size());
        assertCorrectHeader((Section)((Choice)case5.elements(Section.class).get(0)).get(0).get(0));
        assertEquals("second",((Section)((Choice)case5.elements(Section.class).get(0)).get(1).get(0)).getId());
        assertEquals(1,resolution.getResolution((Choice)case5.elements(Section.class).get(0)));

        // This case - a choice inside a choice - makes little sense. It is included as a reminder -
        // what we really want is to be able to include some additional alternatives of a choice,
        // but without any magic. That requires allowing alternative as a top-level tag, or something
        Section case6=(Section)page.getSection().elements(Section.class).get(5);
        Choice includerChoice=(Choice)case6.elements().get(0);
        Choice includedChoice=(Choice)includerChoice.alternatives().get(0).get(0);
        assertCorrectFooter((Section)includedChoice.alternatives().get(0).get(0));
    }

    private void assertCorrectHeader(Section header) {
        assertEquals("row",header.getLayout().getName());
        assertEquals(2,header.elements(Section.class).size());
        assertEquals(       "global",((Source)((Section)header.elements(Section.class).get(0)).elements(Source.class).get(0)).getName());
        assertEquals("notifications",((Source)((Section)header.elements(Section.class).get(1)).elements(Source.class).get(0)).getName());
    }

    private void assertCorrectFooter(Section footer) {
        assertEquals("row",footer.getLayout().getName());
        assertTrue(footer.elements(Section.class).isEmpty());
        assertEquals("popularSearches",((Source)footer.elements(Source.class).get(0)).getName());
    }

    private void assertCorrectGeneric(PageTemplate page) {
        assertEquals("image",   ((Source)((Section)page.getSection().elements(Section.class).get(0)).elements(Source.class).get(0)).getName());
        assertEquals("flickr",  ((Source)((Section)page.getSection().elements(Section.class).get(0)).elements(Source.class).get(1)).getName());
        assertEquals(Source.any,((Section)page.getSection().elements(Section.class).get(1)).elements(Source.class).get(0));
    }

}
