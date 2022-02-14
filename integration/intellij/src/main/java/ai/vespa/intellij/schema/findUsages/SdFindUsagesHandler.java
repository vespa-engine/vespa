// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import ai.vespa.intellij.schema.model.Function;
import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.utils.Path;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import ai.vespa.intellij.schema.SdUtil;
import ai.vespa.intellij.schema.psi.SdFile;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class handles creating the "Find Usages" window.
 *
 * @author Shahar Ariel
 */
public class SdFindUsagesHandler extends FindUsagesHandler {
    
    private final Map<String, List<Function>> functionsMap;
    
    public SdFindUsagesHandler(PsiElement psiElement) {
        super(psiElement);
        PsiFile file = psiElement.getContainingFile();
        functionsMap = file instanceof SdFile ? new Schema((SdFile)file).functions() : Map.of();
    }
    
    @Override
    public boolean processElementUsages(PsiElement elementToSearch,
                                        Processor<? super UsageInfo> processor,
                                        FindUsagesOptions options) {
        SearchScope scope = options.searchScope;
        boolean searchText = options.isSearchForTextOccurrences && scope instanceof GlobalSearchScope;
        
        if (options.isUsages) {
            if (!(elementToSearch instanceof SdFunctionDefinition)) {
                boolean success =
                    ReferencesSearch.search(createSearchParameters(elementToSearch, scope, options))
                                    .forEach((PsiReference ref) -> processor.process(new UsageInfo(ref)));
                if (!success) return false;
            } else {
                String functionName = ReadAction.compute( ((SdFunctionDefinition) elementToSearch)::getName);
                
                for (Function function : functionsMap.get(functionName)) {
                    boolean success =
                        ReferencesSearch.search(createSearchParameters(function.definition(), scope, options))
                                        .forEach((PsiReference ref) -> {
                                            if (ref.getElement().getParent() == elementToSearch) return true; // Skip self ref.
                                            return processor.process(new UsageInfo(ref));
                                        });
                    if (!success) return false;
                }
            }
        }
        if (searchText) {
            if (options.fastTrack != null)
                options.fastTrack.searchCustom(consumer -> processUsagesInText(elementToSearch, processor, (GlobalSearchScope)scope));
            else
                return processUsagesInText(elementToSearch, processor, (GlobalSearchScope)scope);
        }
        return true;
    }
    
}
