// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.intellij.sdk.language.SdUtil;
import org.intellij.sdk.language.psi.SdFile;
import org.intellij.sdk.language.psi.SdFunctionDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;

/**
 * This class handles creating the "Find Usages" window.
 * @author shahariel
 */
public class SdFindUsagesHandler extends FindUsagesHandler {
    
    protected HashMap<String, List<PsiElement>> macrosMap;
    
    protected SdFindUsagesHandler(@NotNull PsiElement psiElement) {
        super(psiElement);
        PsiFile file = psiElement.getContainingFile();
        macrosMap = file instanceof SdFile ? SdUtil.createMacrosMap((SdFile) psiElement.getContainingFile()) : new HashMap<>();
    }
    
    @Override
    public boolean processElementUsages(@NotNull final PsiElement elementToSearch,
                                        @NotNull final Processor<? super UsageInfo> processor,
                                        @NotNull final FindUsagesOptions options) {
        final ReadActionProcessor<PsiReference> refProcessor = new ReadActionProcessor<>() {
            @Override
            public boolean processInReadAction(final PsiReference ref) {
                return processor.process(new UsageInfo(ref));
            }
        };
        
        final SearchScope scope = options.searchScope;
        
        final boolean searchText = options.isSearchForTextOccurrences && scope instanceof GlobalSearchScope;
        
        if (options.isUsages) {
            if (!(elementToSearch instanceof SdFunctionDefinition)) {
                boolean success =
                    ReferencesSearch.search(createSearchParameters(elementToSearch, scope, options)).forEach(refProcessor);
                if (!success) return false;
            } else {
                String macroName = ReadAction.compute( ((SdFunctionDefinition) elementToSearch)::getName);
                
                for (PsiElement macroImpl : macrosMap.get(macroName)) {
                    boolean success =
                        ReferencesSearch.search(createSearchParameters(macroImpl, scope, options)).forEach(refProcessor);
                    if (!success) return false;
                }
            }
        }
        if (searchText) {
            if (options.fastTrack != null) {
                options.fastTrack.searchCustom(consumer -> processUsagesInText(elementToSearch, processor, (GlobalSearchScope)scope));
            }
            else {
                return processUsagesInText(elementToSearch, processor, (GlobalSearchScope)scope);
            }
        }
        
        return true;
    }
    
}
