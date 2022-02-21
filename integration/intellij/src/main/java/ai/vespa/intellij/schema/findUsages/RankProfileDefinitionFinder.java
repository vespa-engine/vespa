// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import ai.vespa.intellij.schema.model.RankProfile;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import ai.vespa.intellij.schema.utils.AST;
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
public class RankProfileDefinitionFinder extends UsageFinder {

    private final PsiElement referringElement;
    private final String profileNameToFind;
    private final Set<RankProfile> visited = new HashSet<>();

    public RankProfileDefinitionFinder(PsiElement referringElement, SearchScope scope, Processor<? super UsageInfo> processor) {
        super(scope, processor);
        this.referringElement = referringElement;
        this.profileNameToFind = ReadAction.compute(() -> referringElement.getText());
    }

    /** Returns true if this finder is appropriate for this task, false if others should be tried. */
    public boolean findDefinition() {
        RankProfile referringProfile = ReadAction.compute(() -> resolveReferringProfile());
        if (referringProfile == null) return false;
        findDefinitionAbove(referringProfile);
        return true;
    }

    /**
     * Returns the profile which inherits the one we're to find the definition of, or null if
     * referringElement is not the name of an inherited profile.
     */
    private RankProfile resolveReferringProfile() {
        SdRankProfileDefinition profileDefinition = PsiTreeUtil.getParentOfType(referringElement, SdRankProfileDefinition.class);
        if (profileDefinition == null) return null;
        if (AST.inherits(profileDefinition).stream().noneMatch(node -> node.getText().equals(referringElement.getText()))) return null;
        return resolveSchema(profileDefinition).rankProfiles().get(profileDefinition.getName());
    }

    private void findDefinitionAbove(RankProfile profile) {
        ProgressIndicatorProvider.checkCanceled();
        if ( ! visited.add(profile)) return;
        ReadAction.compute(() -> findDefinitionIn(profile));
        for (var parent : ReadAction.compute(() -> profile.parents().values()))
            findDefinitionAbove(parent);
    }

    private boolean findDefinitionIn(RankProfile profile) {
        if (profileNameToFind.equals(profile.name()))
            processor().process(new UsageInfo(profile.definition()));
        return true;
    }

}
