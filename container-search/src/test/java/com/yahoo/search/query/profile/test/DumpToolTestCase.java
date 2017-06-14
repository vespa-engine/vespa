// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.test;

import com.yahoo.search.query.profile.DumpTool;

/**
 * @author bratseth
 */
public class DumpToolTestCase extends junit.framework.TestCase {

    String profileDir="src/test/java/com/yahoo/search/query/profile/config/test/multiprofile";

    public void testNoParameters() {
        assertTrue(new DumpTool().resolveAndDump().startsWith("Dumps all resolved"));
    }

    public void testHelpParameter() {
        assertTrue(new DumpTool().resolveAndDump("-help").startsWith("Dumps all resolved"));
    }

    public void testNoDimensionValues() {
        assertTrue(new DumpTool().resolveAndDump("multiprofile1",profileDir).startsWith("a=general-a\n"));
    }

    public void testAllParametersSet() {
        assertTrue(new DumpTool().resolveAndDump("multiprofile1",profileDir,"").startsWith("a=general-a\n"));
    }

    //This test is order dependent. Fix this!!
    public void testVariant() {
        System.out.println(new DumpTool().resolveAndDump("multiprofile1",profileDir,"region=us"));
        assertTrue(new DumpTool().resolveAndDump("multiprofile1",profileDir,"region=us").startsWith("a=us-a\nb=us-b\nregion=us"));
    }

}
