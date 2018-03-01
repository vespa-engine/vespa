// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http.writer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HttpWriterTest {

    private static String defaultTitle = "My Title";
    private static String defaultHeader = "<html>\n"
                                        + "  <head>\n"
                                        + "    <title>My Title</title>\n"
                                        + "  </head>\n"
                                        + "  <body>\n"
                                        + "    <h1>My Title</h1>\n";
    private static String defaultFooter = "  </body>\n"
                                        + "</html>\n";

    @Test
    public void testStructure() {
        HttpWriter writer = new HttpWriter();
        String header = defaultHeader.replace(defaultTitle, "Untitled page");
        assertEquals(header + defaultFooter, writer.toString());
    }

    @Test
    public void testTitle() {
        HttpWriter writer = new HttpWriter().addTitle(defaultTitle);
        assertEquals(defaultHeader + defaultFooter, writer.toString());
    }

    @Test
    public void testParagraph() {
        String paragraph = "This is a paragraph";
        String paragraph2 = "More text";
        HttpWriter writer = new HttpWriter().addTitle(defaultTitle).write(paragraph).write(paragraph2);
        String content = "    <p>\n"
                       + "      " + paragraph + "\n"
                       + "    </p>\n"
                       + "    <p>\n"
                       + "      " + paragraph2 + "\n"
                       + "    </p>\n";
        assertEquals(defaultHeader + content + defaultFooter, writer.toString());
    }

    @Test
    public void testLink() {
        String name = "My link";
        String link = "/foo/bar?hmm";
        HttpWriter writer = new HttpWriter().addTitle(defaultTitle).writeLink(name, link);
        String content = "    <a href=\"" + link + "\">" + name + "</a>\n";
        assertEquals(defaultHeader + content + defaultFooter, writer.toString());
    }

    @Test
    public void testErrors() {
        try{
            HttpWriter writer = new HttpWriter().addTitle(defaultTitle);
            writer.toString();
            writer.write("foo");
            assertTrue(false);
        } catch (IllegalStateException e) {
        }
        try{
            new HttpWriter().write("foo").addTitle("bar");
            assertTrue(false);
        } catch (IllegalStateException e) {
        }
    }

}
