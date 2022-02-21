// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.hierarchy;

import ai.vespa.intellij.schema.model.Function;
import ai.vespa.intellij.schema.model.RankProfile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import ai.vespa.intellij.schema.psi.SdExpressionDefinition;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;
import ai.vespa.intellij.schema.psi.SdIdentifier;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Callee tree in the "Call Hierarchy" window.
 *
 * @author Shahar Ariel
 */
public class SdCalleeTreeStructure extends SdCallTreeStructure {
    
    public SdCalleeTreeStructure(Project project, PsiElement element, String currentScopeType) {
        super(project, element, currentScopeType);
    }
        
    @Override
    protected Set<Function> getChildren(SdFunctionDefinition element) {
        return getCallees(element, functionsMap);
    }
    
    private Set<Function> getCallees(SdFunctionDefinition function, Map<String, List<Function>> functions) {
        Set<Function> results = new HashSet<>();
        SdExpressionDefinition expression = PsiTreeUtil.findChildOfType(function, SdExpressionDefinition.class);
        if (expression == null) {
            return results;
        }
        for (SdIdentifier identifier : PsiTreeUtil.collectElementsOfType(expression, SdIdentifier.class)) {
            if (functions.containsKey(((PsiNamedElement) identifier).getName())) {
                PsiReference identifierRef = identifier.getReference();
                if (identifierRef != null) {
                    results.add(Function.from((SdFunctionDefinition)identifierRef.resolve(), null));
                }
            }
        }
        
        SdRankProfileDefinition rankProfile = PsiTreeUtil.getParentOfType(function, SdRankProfileDefinition.class);
        if (rankProfile == null) {
            return results;
        }
        String rankProfileName = rankProfile.getName();
        if (!ranksHeritageMap.containsKey(rankProfileName)) {
            ranksHeritageMap.put(rankProfileName, SdHierarchyUtil.getRankProfileChildren(myFile, new RankProfile(rankProfile, null)));
        }
        
        Set<SdRankProfileDefinition> inheritedRanks = ranksHeritageMap.get(rankProfileName);
        
        for (Function functionImpl : functions.get(function.getName())) {
            if (inheritedRanks.contains(PsiTreeUtil.getParentOfType(functionImpl.definition(), SdRankProfileDefinition.class))) {
                results.add(functionImpl);
            }

        }
        
        return results;
    }
    
}
