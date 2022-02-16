// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import ai.vespa.intellij.schema.model.Function;
import ai.vespa.intellij.schema.model.RankProfile;
import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import ai.vespa.intellij.schema.utils.Path;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;

import java.util.Collection;
import java.util.List;

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
                findFunctionUsages((SdFunctionDefinition) elementToSearch, processor);
            } else {
                boolean success =
                        ReferencesSearch.search(createSearchParameters(elementToSearch, options.searchScope, options))
                                        .forEach((PsiReference ref) -> processor.process(new UsageInfo(ref)));
                if (!success) return false;
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

    /**
     * Finds usages brute force. There is built-in search functionality in the IntelliJ SDK but I could
     * not make it work across files. Since the lexical, scope of a rank profile will be quite small
     * brute force might be faster in any case.
     *
     * Since search is done by a separate thread it cannot safely access the Psi tree.
     * This splits Psi tree accesses into smaller chunks which are handed off to the Reader
     * on the assumption that this keeps the IDE responsive. I have not found documentation
     * on that.
     */
    private void findFunctionUsages(SdFunctionDefinition functionToFind, Processor<? super UsageInfo> processor) {
        String functionNameToFind = ReadAction.compute(functionToFind::getName);
        Schema schema = ReadAction.compute(this::resolveSchema);
        var rankProfile = ReadAction.compute(() -> schema.rankProfiles()
                                                         .get(PsiTreeUtil.getParentOfType(functionToFind, SdRankProfileDefinition.class).getName()));
        findFunctionUsages(functionNameToFind, functionToFind, rankProfile, processor);
    }

    private Schema resolveSchema() {
        PsiFile file = getPsiElement().getContainingFile();
        if (file.getVirtualFile().getPath().endsWith(".profile")) {
            Path schemaFile = Path.fromString(file.getVirtualFile().getParent().getPath() + ".sd");
            return ReadAction.compute(() -> Schema.fromProjectFile(getProject(), schemaFile));
        }
        else { // schema
            return ReadAction.compute(() -> Schema.fromProjectFile(getProject(), Path.fromString(file.getVirtualFile().getPath())));
        }
    }

    private void findFunctionUsages(String functionNameToFind,
                                    SdFunctionDefinition functionToFind,
                                    RankProfile rankProfile,
                                    Processor<? super UsageInfo> processor) {
        ReadAction.compute(() -> findFunctionUsagesInThis(functionNameToFind, functionToFind, rankProfile, processor));
        Collection<RankProfile> children = ReadAction.compute(() -> rankProfile.children().values());
        for (var child : children)
            findFunctionUsages(functionNameToFind, functionToFind, child, processor);
    }

    private boolean findFunctionUsagesInThis(String functionNameToFind,
                                             SdFunctionDefinition functionToFind,
                                             RankProfile rankProfile,
                                             Processor<? super UsageInfo> processor) {
        Collection<List<Function>> functions = ReadAction.compute(() -> rankProfile.definedFunctions().values());
        for (var functionList : functions) {
            for (var function : functionList) {
                String text = ReadAction.compute(() -> function.definition().getText());
                int offset = 0;
                boolean skipNext = functionToFind == function.definition(); // Skip the definition itself
                while (offset < text.length()) {
                    int occurrenceStart = text.indexOf(functionNameToFind, offset);
                    if (occurrenceStart < 0) break;
                    int occurrenceEnd = occurrenceStart + functionNameToFind.length();
                    if ( ! skipNext)
                        processor.process(new UsageInfo(function.definition(), occurrenceStart, occurrenceEnd));
                    offset = occurrenceEnd;
                    skipNext = false;
                }
            }
        }
        return true;
    }

}
