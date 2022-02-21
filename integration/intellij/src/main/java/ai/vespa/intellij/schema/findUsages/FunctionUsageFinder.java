// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import ai.vespa.intellij.schema.model.Function;
import ai.vespa.intellij.schema.model.RankProfile;
import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An instance created to find usages of a function once.
 *
 * @author bratseth
 */
public class FunctionUsageFinder extends UsageFinder {

    private final SdFunctionDefinition functionToFind;
    private final String functionNameToFind;
    private final Set<RankProfile> visited = new HashSet<>();

    public FunctionUsageFinder(SdFunctionDefinition functionToFind, SearchScope scope, Processor<? super UsageInfo> processor) {
        super(scope, processor);
        this.functionToFind = functionToFind;
        this.functionNameToFind = ReadAction.compute(functionToFind::getName);
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
    public void findUsages() {
        Schema schema = ReadAction.compute(() -> resolveSchema(functionToFind));
        var rankProfile = ReadAction.compute(() -> schema.rankProfiles()
                                                         .get(PsiTreeUtil.getParentOfType(functionToFind, SdRankProfileDefinition.class).getName()));
        findUsagesBelow(rankProfile);
    }

    private void findUsagesBelow(RankProfile profile) {
        ProgressIndicatorProvider.checkCanceled();
        if ( ! visited.add(profile)) return;
        ReadAction.compute(() -> findUsagesIn(profile));
        for (var child : ReadAction.compute(() -> profile.children()))
            findUsagesBelow(child);
    }

    private boolean findUsagesIn(RankProfile profile) {
        if ( ! scope().contains(profile.definition().getContainingFile().getVirtualFile())) return false;
        Collection<List<Function>> functions = ReadAction.compute(() -> profile.definedFunctions().values());
        for (var functionList : functions) {
            for (var function : functionList) {
                var matchingVisitor = new MatchingVisitor(functionNameToFind,
                                                          functionToFind == function.definition(),
                                                          processor());
                ReadAction.compute(() -> { function.definition().accept(matchingVisitor); return null; } );
            }
        }
        return true;
    }

    private static class MatchingVisitor extends PsiElementVisitor {

        private final String textToMatch;
        private final Processor<? super UsageInfo> processor;

        private boolean skipNextMatch;

        public MatchingVisitor(String textToMatch, boolean skipFirstMatch, Processor<? super UsageInfo> processor) {
            this.textToMatch = textToMatch;
            this.skipNextMatch = skipFirstMatch;
            this.processor = processor;
        }

        @Override
        public void visitElement(PsiElement element) {
            if (element instanceof LeafPsiElement)
                visitThis(element);
            else
                element.acceptChildren(this);
        }

        private void visitThis(PsiElement element) {
            if ( ! textToMatch.equals(element.getText())) return;
            if (skipNextMatch) {
                skipNextMatch = false;
                return;
            }
            processor.process(new UsageInfo(element));
        }

    }

}
