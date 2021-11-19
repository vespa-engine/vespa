// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.hierarchy;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import ai.vespa.intellij.schema.psi.SdExpressionDefinition;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;
import ai.vespa.intellij.schema.psi.SdIdentifier;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * A Callee tree in the "Call Hierarchy" window.
 *
 * @author Shahar Ariel
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
            if (macrosMap.containsKey(((PsiNamedElement) identifier).getName())) {
                PsiReference identifierRef = identifier.getReference();
                if (identifierRef != null) {
                    results.add(identifierRef.resolve());
                }
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
