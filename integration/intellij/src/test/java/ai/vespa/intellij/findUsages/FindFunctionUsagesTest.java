// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.findUsages;

import ai.vespa.intellij.PluginTestBase;
import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.utils.Path;
import com.intellij.usageView.UsageInfo;
import org.junit.Test;

/**
 * @author bratseth
 */
public class FindFunctionUsagesTest extends PluginTestBase {

    @Test
    public void testFindUsagesInRankProfileModularity() {
        useDir("src/test/applications/rankprofilemodularity");
        var tester = new UsagesTester("test.sd", getProject());
        tester.assertFunctionUsages("0 refs",       0, "in_schema1", "tensorFunction");
        tester.assertFunctionUsages("1 local ref in first-phase",  1, "in_schema2", "f2");
        tester.assertFunctionUsages("2 local refs", 2, "in_schema2", "ff1");
        tester.assertFunctionUsages("1 local ref", 1, "in_schema4", "f2");
        tester.assertFunctionUsages("1 local ref", 1, "outside_schema1", "local1");
        tester.assertFunctionUsages("4 local refs", 4, "outside_schema1", "local12");
        tester.assertFunctionUsages("3 refs in parent schema", 3, "outside_schema2", "fo2");
    }

    @Test
    public void testFindUsagesInSchemaInheritance() {
        useDir("src/test/applications/schemainheritance");
        var tester = new UsagesTester("parent.sd", getProject());
        tester.assertFunctionUsages("1 ref in child schema", 1, "parent_profile", "parentFunction");
    }

    @Test
    public void testUsageDetails() {
        useDir("src/test/applications/rankprofilemodularity");
        var tester = new UsagesTester("test.sd", getProject());
        UsageInfo usage = tester.assertFunctionUsages("1 local ref", 1, "outside_schema1", "local1").get(0);
        assertEquals(93, usage.getNavigationOffset());
        assertEquals(93 + 6, usage.getNavigationRange().getEndOffset());
    }

}
