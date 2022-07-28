// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.content.ContentSearch;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Simon Thoresen Hult
 */
public class DomContentApplicationBuilderTest {

    @Test
    void requireThatDefaultsAreNull() throws Exception {
        ContentSearch search = newContentSearch(
                "<content/>");
        assertNull(search.getVisibilityDelay());
        assertNull(search.getQueryTimeout());
    }

    @Test
    void requireThatEmptySearchIsSafe() throws Exception {
        ContentSearch search = newContentSearch(
                "<content>" +
                        "  <search/>" +
                        "</content>");
        assertNull(search.getVisibilityDelay());
        assertNull(search.getQueryTimeout());
    }

    @Test
    void requireThatContentSearchCanBeBuilt() throws Exception {
        ContentSearch search = newContentSearch(
                "<content>" +
                        "  <search>" +
                        "    <query-timeout>1.1</query-timeout>" +
                        "    <visibility-delay>2.3</visibility-delay>" +
                        "  </search>" +
                        "</content>");
        assertEquals(1.1, search.getQueryTimeout(), 1E-6);
        assertEquals(2.3, search.getVisibilityDelay(), 1E-6);
    }

    private static ContentSearch newContentSearch(String xml) throws Exception {
        return DomContentSearchBuilder.build(
                new ModelElement(DocumentBuilderFactory.newInstance()
                                                       .newDocumentBuilder()
                                                       .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                                                       .getDocumentElement()));
    }
}
