// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.search.query.profile.DumpTool;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class DumpToolTestCase {

    String profileDir="src/test/java/com/yahoo/search/query/profile/config/test/multiprofile";

    @Test
    public void testNoParameters() {
        assertTrue(new DumpTool().resolveAndDump().startsWith("Dumps all resolved"));
    }

    @Test
    public void testHelpParameter() {
        assertTrue(new DumpTool().resolveAndDump("-help").startsWith("Dumps all resolved"));
    }

    @Test
    public void testNoDimensionValues() {
        assertTrue(new DumpTool().resolveAndDump("multiprofile1",profileDir).startsWith("a=general-a\n"));
    }

    @Test
    public void testAllParametersSet() {
        assertTrue(new DumpTool().resolveAndDump("multiprofile1",profileDir,"").startsWith("a=general-a\n"));
    }

    // This test is order dependent. Fix this!!
    @Test
    public void testVariant() {
        assertTrue(new DumpTool().resolveAndDump("multiprofile1",profileDir,"region=us").startsWith("a=us-a\nb=us-b\nregion=us"));
    }

}
