// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.findUsages;

import ai.vespa.intellij.PluginTestBase;
import ai.vespa.intellij.schema.SdUtil;
import ai.vespa.intellij.schema.findUsages.SdFindUsagesHandler;
import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.utils.Path;
import com.intellij.find.findUsages.FindUsagesOptions;
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
        Schema schema = Schema.fromProjectFile(getProject(), Path.fromString("test.sd"));
        var handler = new SdFindUsagesHandler(schema.definition());
        var function = SdUtil.functionsIn(schema.rankProfile("in_schema2").get()).get("ff1").get(0).definition();
        var usageProcessor = new MockUsageProcessor();
        var options = new FindUsagesOptions(getProject());
        options.isUsages = true;
        handler.processElementUsages(function, usageProcessor, options);
        assertEquals(3, usageProcessor.usages.size());
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
