// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.findUsages;

import ai.vespa.intellij.PluginTestBase;
import ai.vespa.intellij.schema.SdUtil;
import ai.vespa.intellij.schema.findUsages.SdFindUsagesHandler;
import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.utils.Path;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.project.Project;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bratseth
 */
public class FindUsagesTest extends PluginTestBase {

    @Test
    public void testFindUsages() {
        useDir("src/test/applications/rankprofilemodularity");
        var tester = new UsagesTester("test.sd", getProject());
        tester.assertFunctionUsages("Self reference + 2 local references", 3, "in_schema2", "ff1");
    }

    private static class UsagesTester {

        final Project project;
        final Schema schema;
        final SdFindUsagesHandler handler;

        UsagesTester(String schemaName, Project project) {
            this.project = project;
            this.schema = Schema.fromProjectFile(project, Path.fromString(schemaName));
            this.handler = new SdFindUsagesHandler(schema.definition());
        }

        void assertFunctionUsages(String explanation, int expectedUsages, String profileName, String functionName) {
            var function = SdUtil.functionsIn(schema.rankProfile(profileName).get()).get(functionName).get(0).definition();
            var usageProcessor = new MockUsageProcessor();
            var options = new FindUsagesOptions(project);
            options.isUsages = true;
            handler.processElementUsages(function, usageProcessor, options);
            assertEquals(explanation, expectedUsages, usageProcessor.usages.size());
        }

    }

    private static class MockUsageProcessor implements Processor<UsageInfo> {

        List<UsageInfo> usages = new ArrayList<>();

        @Override
        public boolean process(UsageInfo usageInfo) {
            usages.add(usageInfo);
            return true;
        }

    }

}
