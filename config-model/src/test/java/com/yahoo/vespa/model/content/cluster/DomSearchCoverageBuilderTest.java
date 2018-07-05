// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.SearchCoverage;
import org.apache.commons.io.input.CharSequenceInputStream;
import org.junit.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Simon Thoresen Hult
 */
public class DomSearchCoverageBuilderTest {

    @Test
    public void requireThatDefaultsAreNull() throws Exception {
        SearchCoverage coverage = newSearchCoverage(
                "<content/>");
        assertNull(coverage.getMinimum());
        assertNull(coverage.getMinWaitAfterCoverageFactor());
        assertNull(coverage.getMaxWaitAfterCoverageFactor());
    }

    @Test
    public void requireThatEmptySearchIsSafe() throws Exception {
        SearchCoverage coverage = newSearchCoverage(
                "<content>" +
                "  <search/>" +
                "</content>");
        assertNull(coverage.getMinimum());
        assertNull(coverage.getMinWaitAfterCoverageFactor());
        assertNull(coverage.getMaxWaitAfterCoverageFactor());
    }

    @Test
    public void requireThatEmptyCoverageIsSafe() throws Exception {
        SearchCoverage coverage = newSearchCoverage(
                "<content>" +
                "  <search>" +
                "    <coverage/>" +
                "  </search>" +
                "</content>");
        assertNull(coverage.getMinimum());
        assertNull(coverage.getMinWaitAfterCoverageFactor());
        assertNull(coverage.getMaxWaitAfterCoverageFactor());
    }

    @Test
    public void requireThatSearchCoverageCanBeBuilt() throws Exception {
        SearchCoverage coverage = newSearchCoverage(
                "<content>" +
                "  <search>" +
                "    <coverage>" +
                "      <minimum>0.11</minimum>" +
                "      <min-wait-after-coverage-factor>0.23</min-wait-after-coverage-factor>" +
                "      <max-wait-after-coverage-factor>0.58</max-wait-after-coverage-factor>" +
                "    </coverage>" +
                "  </search>" +
                "</content>");
        assertEquals(0.11, coverage.getMinimum(), 1E-6);
        assertEquals(0.23, coverage.getMinWaitAfterCoverageFactor(), 1E-6);
        assertEquals(0.58, coverage.getMaxWaitAfterCoverageFactor(), 1E-6);
    }

    private static SearchCoverage newSearchCoverage(String xml) throws Exception {
        return DomSearchCoverageBuilder.build(
                new ModelElement(DocumentBuilderFactory.newInstance()
                                                       .newDocumentBuilder()
                                                       .parse(new CharSequenceInputStream(xml, StandardCharsets.UTF_8))
                                                       .getDocumentElement()));
    }
}
