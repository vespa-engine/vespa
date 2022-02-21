// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.findUsages;

import ai.vespa.intellij.PluginTestBase;
import com.intellij.usageView.UsageInfo;
import org.junit.Test;

/**
 * @author bratseth
 */
public class FindRankProfileUsagesTest extends PluginTestBase {

    @Test
    public void testFindUsagesInRankProfileModularity() {
        useDir("src/test/applications/rankprofilemodularity");
        var tester = new UsagesTester("test.sd", getProject());
        tester.assertProfileUsages(2, "in_schema1");
        tester.assertProfileUsages(0, "in_schema2");
        tester.assertProfileUsages(1, "outside_schema1");
        tester.assertProfileUsages(4, "outside_schema2");
    }

    @Test
    public void testFindUsagesInSchemaInheritance() {
        useDir("src/test/applications/schemainheritance");
        var tester = new UsagesTester("parent.sd", getProject());
        UsageInfo usage = tester.assertProfileUsages(1, "parent_profile").get(0);
        assertEquals(406, usage.getNavigationOffset());
        assertEquals(560, usage.getNavigationRange().getEndOffset());
    }

}
