// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.findUsages;

import ai.vespa.intellij.PluginTestBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bratseth
 */
public class FindDefinitionTest extends PluginTestBase {

    @Test
    public void testFindUsagesInRankProfileModularity() {
        useDir("src/test/applications/rankprofilemodularity");
        var tester = new UsagesTester("test.sd", getProject());
        UsageInfo usage = tester.findFunctionDefinition("outside_schema1", 93);
    }

}
