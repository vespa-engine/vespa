// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;

/**
 * This class handles creating the "Find Usages" window.
 *
 * @author Shahar Ariel
 * @author bratseth
 */
public class SdFindUsagesHandler extends FindUsagesHandler {
    
    public SdFindUsagesHandler(PsiElement psiElement) {
        super(psiElement);
    }
    
    @Override
    public boolean processElementUsages(PsiElement elementToSearch,
                                        Processor<? super UsageInfo> processor,
                                        FindUsagesOptions options) {
        if (options.isUsages) {
            if (elementToSearch instanceof SdFunctionDefinition) {
                new FunctionUsageFinder((SdFunctionDefinition) elementToSearch, options.searchScope, processor).findUsages();
            }
            else if (elementToSearch instanceof SdRankProfileDefinition) {
                new RankProfileUsageFinder((SdRankProfileDefinition) elementToSearch, options.searchScope, processor).findUsages();
            }
            else { // try find definitions
                boolean suitable = new RankProfileDefinitionFinder(elementToSearch, options.searchScope, processor).findDefinition();
                if ( ! suitable)
                    new FunctionDefinitionFinder(elementToSearch, options.searchScope, processor).findDefinition();
            }
        }

        if (options.isSearchForTextOccurrences && options.searchScope instanceof GlobalSearchScope) {
            if (options.fastTrack != null)
                options.fastTrack.searchCustom(consumer -> processUsagesInText(elementToSearch,
                                                                               processor,
                                                                               (GlobalSearchScope)options.searchScope));
            else
                return processUsagesInText(elementToSearch, processor, (GlobalSearchScope)options.searchScope);
        }

        return true;
    }

}
