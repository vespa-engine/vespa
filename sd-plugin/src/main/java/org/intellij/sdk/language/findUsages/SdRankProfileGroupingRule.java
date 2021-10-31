// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.findUsages;

import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import org.intellij.sdk.language.SdLanguage;
import org.intellij.sdk.language.psi.SdRankProfileDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class represent a Rank Profile that groups elements in the "Find Usages" window.
 * @author Shahar Ariel
 */
public class SdRankProfileGroupingRule extends SingleParentUsageGroupingRule implements DumbAware {

    @Override
    protected @Nullable UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
        PsiElement psiElement = usage instanceof PsiElementUsage ? ((PsiElementUsage)usage).getElement() : null;
        if (psiElement == null || psiElement.getLanguage() != SdLanguage.INSTANCE) return null;
        
        while (psiElement != null) {
            if (psiElement instanceof SdRankProfileDefinition) {
                final SdRankProfileDefinition componentElement = (SdRankProfileDefinition) psiElement;
                return new SdUsageGroup(componentElement);
            }
            psiElement = psiElement.getParent();
        }

        return null;
    }
}
