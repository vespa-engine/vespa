// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.hierarchy;

import ai.vespa.intellij.schema.model.Function;
import ai.vespa.intellij.schema.psi.SdSecondPhaseDefinition;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import ai.vespa.intellij.schema.psi.SdFirstPhaseDefinition;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A Caller tree in the "Call Hierarchy" window.
 *
 * @author Shahar Ariel
 */
public class SdCallerTreeStructure extends SdCallTreeStructure {
    
    private Map<String, Set<Function>> functionTreeChildren;
    
    public SdCallerTreeStructure(Project project, PsiElement element, String currentScopeType) {
        super(project, element, currentScopeType);
        functionTreeChildren = new HashMap<>();
    }
    
    @Override
    protected Set<Function> getChildren(SdFunctionDefinition element) {
        return getCallers(element, functionsMap);
    }
    
    private Set<Function> getCallers(SdFunctionDefinition function, Map<String, List<Function>> functionsMap) {
        String functionName = function.getName();
        if (functionTreeChildren.containsKey(functionName)) {
            return functionTreeChildren.get(functionName);
        }
        
        Set<Function> results = new HashSet<>();
        for (Function functionImpl : functionsMap.get(functionName)) {
            SearchScope searchScope = getSearchScope(myScopeType, functionImpl.definition());
            ReferencesSearch.search(functionImpl.definition(), searchScope).forEach((Consumer<? super PsiReference>) r -> {
                ProgressManager.checkCanceled();
                PsiElement psiElement = r.getElement();
                SdFunctionDefinition f = PsiTreeUtil.getParentOfType(psiElement, SdFunctionDefinition.class, false);
                if (f != null && f.getName() != null && !f.getName().equals(functionName)) {
                    results.add(Function.from(f, null));
                } else {
                    SdFirstPhaseDefinition fp = PsiTreeUtil.getParentOfType(psiElement, SdFirstPhaseDefinition.class, false);
                    if (fp != null)
                        results.add(Function.from(fp, null));
                    else {
                        SdSecondPhaseDefinition sp = PsiTreeUtil.getParentOfType(psiElement, SdSecondPhaseDefinition.class, false);
                        if (sp != null)
                            results.add(Function.from(sp, null));
                    }
                }
            });
        }
        
        functionTreeChildren.put(functionName, results);
        return results;
    }
    
}
