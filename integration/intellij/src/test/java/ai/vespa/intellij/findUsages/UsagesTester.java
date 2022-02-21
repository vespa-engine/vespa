// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.findUsages;

import ai.vespa.intellij.schema.findUsages.SdFindUsagesHandler;
import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.utils.Path;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

class UsagesTester {

    final Project project;
    final Schema schema;
    final SdFindUsagesHandler handler;
    final MockUsageProcessor usageProcessor = new MockUsageProcessor();

    UsagesTester(String schemaName, Project project) {
        this.project = project;
        this.schema = Schema.fromProjectFile(project, Path.fromString(schemaName));
        this.handler = new SdFindUsagesHandler(schema.definition());
    }

    List<UsageInfo> assertFunctionUsages(String explanation, int expectedUsages, String profileName, String functionName) {
        var profile = schema.rankProfiles().get(profileName);
        var functions = profile.definedFunctions().get(functionName);
        var function = functions.get(0).definition();
        findUsages(function);
        assertEquals(explanation, expectedUsages, usageProcessor.usages.size());
        return usageProcessor.usages;
    }

    List<UsageInfo> assertProfileUsages(int expectedUsages, String profileName) {
        var profile = schema.rankProfiles().get(profileName).definition();
        findUsages(profile);
        assertEquals(expectedUsages, usageProcessor.usages.size());
        return usageProcessor.usages;
    }

    /** Finds the function referred at the given character offset in the given profile. */
    UsageInfo findFunctionDefinition(String profileName, int offset) {
        PsiElement referringElement = schema.rankProfiles().get(profileName).definition().findElementAt(offset);
        findUsages(referringElement);
        assertEquals("Expected to find definition of " + referringElement.getText(),
                     1, usageProcessor.usages.size());
        return usageProcessor.usages.get(0);
    }

    /** Finds the rank profile referred (by inheritance) at the given character offset in the given profile. */
    UsageInfo findRankProfileDefinition(String profileName, int offset) {
        PsiElement referringElement = schema.rankProfiles().get(profileName).definition().findElementAt(offset);
        findUsages(referringElement);
        assertEquals("Expected to find definition of " + referringElement.getText(),
                     1, usageProcessor.usages.size());
        return usageProcessor.usages.get(0);
    }

    private void findUsages(PsiElement element) {
        var options = new FindUsagesOptions(project);
        usageProcessor.usages.clear();
        options.isUsages = true;
        handler.processElementUsages(element, usageProcessor, options);
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
