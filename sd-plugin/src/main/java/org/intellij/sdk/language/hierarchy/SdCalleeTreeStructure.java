// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.hierarchy;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.sdk.language.SdReference;
import org.intellij.sdk.language.psi.SdExpressionDefinition;
import org.intellij.sdk.language.psi.SdFunctionDefinition;
import org.intellij.sdk.language.psi.SdIdentifier;
import org.intellij.sdk.language.psi.SdIdentifierVal;
import org.intellij.sdk.language.psi.SdRankProfileDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * This class represents a Callee tree in the "Call Hierarchy" window.
 * @author shahariel
 */
public class SdCalleeTreeStructure extends SdCallTreeStructure {
    
    public SdCalleeTreeStructure(Project project, PsiElement element, String currentScopeType) {
        super(project, element, currentScopeType);
    }
        
    @NotNull
    @Override
    protected HashSet<PsiElement> getChildren(@NotNull SdFunctionDefinition element) {
        return getCallees(element, macrosMap);
    }
    
    private HashSet<PsiElement> getCallees(@NotNull SdFunctionDefinition macro, HashMap<String, List<PsiElement>> macrosMap) {
        final HashSet<PsiElement> results = new HashSet<>();
        SdExpressionDefinition expression = PsiTreeUtil.findChildOfType(macro, SdExpressionDefinition.class);
        if (expression == null) {
            return results;
        }
        for (SdIdentifier identifier : PsiTreeUtil.collectElementsOfType(expression, SdIdentifier.class)) {
            if (macrosMap.containsKey(identifier.getName())) {
                results.add(identifier.getReference().resolve());
            }
        }
        
        SdRankProfileDefinition rankProfile = PsiTreeUtil.getParentOfType(macro, SdRankProfileDefinition.class);
        if (rankProfile == null) {
            return results;
        }
        String rankProfileName = rankProfile.getName();
        if (!ranksHeritageMap.containsKey(rankProfileName)) {
            ranksHeritageMap.put(rankProfileName, SdHierarchyUtil.getRankProfileChildren(myFile, rankProfile));
        }
        
        HashSet<SdRankProfileDefinition> inheritedRanks = ranksHeritageMap.get(rankProfileName);
        
        for (PsiElement macroImpl : macrosMap.get(macro.getName())) {
            if (inheritedRanks.contains(PsiTreeUtil.getParentOfType(macroImpl, SdRankProfileDefinition.class))) {
                results.add(macroImpl);
            }

        }
        
        return results;
    }
    
    
}
