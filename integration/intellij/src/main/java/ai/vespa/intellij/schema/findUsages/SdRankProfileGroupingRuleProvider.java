// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.usages.impl.FileStructureGroupRuleProvider;
import com.intellij.usages.rules.UsageGroupingRule;

/**
 * This class is used for the extension (in plugin.xml) to the class SdRankProfileGroupingRule.
 *
 * @author Shahar Ariel
 */
public class SdRankProfileGroupingRuleProvider implements FileStructureGroupRuleProvider {

    @Override
    public UsageGroupingRule getUsageGroupingRule(Project project) {
        return new SdRankProfileGroupingRule();
    }

}
