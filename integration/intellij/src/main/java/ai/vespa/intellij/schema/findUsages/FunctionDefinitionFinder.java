// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import ai.vespa.intellij.schema.model.RankProfile;
import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;

import java.util.HashSet;
import java.util.Set;

/**
 * Finds the definition of a function.
 *
 * @author bratseth
 */
public class FunctionDefinitionFinder extends UsageFinder {

    private final PsiElement referringElement;
    private final String functionNameToFind;
    private final Set<RankProfile> visited = new HashSet<>();

    public FunctionDefinitionFinder(PsiElement referringElement, SearchScope scope, Processor<? super UsageInfo> processor) {
        super(scope, processor);
        this.referringElement = referringElement;
        this.functionNameToFind = ReadAction.compute(() -> referringElement.getText());
    }

    public void findDefinition() {
        SdRankProfileDefinition profileDefinition = ReadAction.compute(() -> PsiTreeUtil.getParentOfType(referringElement, SdRankProfileDefinition.class));
        if (profileDefinition == null) return;
        Schema schema = ReadAction.compute(() -> resolveSchema(profileDefinition));
        RankProfile profile = ReadAction.compute(() -> schema.rankProfiles().get(profileDefinition.getName()));
        findDefinitionAbove(profile);
    }

    private void findDefinitionAbove(RankProfile profile) {
        ProgressIndicatorProvider.checkCanceled();
        if ( ! visited.add(profile)) return;
        ReadAction.compute(() -> findDefinitionIn(profile));
        for (var parent : ReadAction.compute(() -> profile.parents().values()))
            findDefinitionAbove(parent);
    }

    private boolean findDefinitionIn(RankProfile profile) {
        for (var entry : profile.definedFunctions().entrySet()) {
            // TODO: Resolve the right function in the list by parameter count
            if (entry.key().equals(functionNameToFind) && entry.getValue().size() > 0)
                processor().process(new UsageInfo(entry.getValue().get(0).definition()));
        }
        return true;
    }

}
