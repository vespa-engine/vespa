// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import ai.vespa.intellij.schema.SdLanguage;
import ai.vespa.intellij.schema.psi.SdDocumentSummaryDefinition;

/**
 * A grouping rule that groups elements in the "Find Usages" window.
 *
 * @author Shahar Ariel
 */
public class SdDocumentSummaryGroupingRule extends SingleParentUsageGroupingRule implements DumbAware {
    
    @Override
    protected UsageGroup getParentGroupFor(Usage usage, UsageTarget[] targets) {
        PsiElement psiElement = usage instanceof PsiElementUsage ? ((PsiElementUsage)usage).getElement() : null;
        if (psiElement == null || psiElement.getLanguage() != SdLanguage.INSTANCE) return null;
    
        while (psiElement != null) {
            if (psiElement instanceof SdDocumentSummaryDefinition) {
                SdDocumentSummaryDefinition componentElement = (SdDocumentSummaryDefinition) psiElement;
                return new SdUsageGroup(componentElement);
            }
            psiElement = psiElement.getParent();
        }
    
        return null;
    }

}
