// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import ai.vespa.intellij.schema.model.RankProfile;
import ai.vespa.intellij.schema.model.Schema;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.search.SearchScope;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;

import java.util.HashSet;
import java.util.Set;

/**
 * An instance created to find usages of a rank profile once.
 *
 * @author bratseth
 */
public class RankProfileUsageFinder extends UsageFinder {

    private final RankProfile profile;
    private final Set<RankProfile> visited = new HashSet<>();

    public RankProfileUsageFinder(SdRankProfileDefinition profileDefinitionToFind, SearchScope scope, Processor<? super UsageInfo> processor) {
        super(scope, processor);
        this.profile = ReadAction.compute(() -> resolveSchema(profileDefinitionToFind).rankProfiles().get(profileDefinitionToFind.getName()));
    }

    public void findUsages() {
        findUsagesBelow(profile);
    }

    private void findUsagesBelow(RankProfile profile) {
        ProgressIndicatorProvider.checkCanceled();
        if ( ! visited.add(profile)) return;

        if ( ! profile.equals(this.profile))
            ReadAction.compute(() -> processor().process(new UsageInfo(profile.definition())));
        for (var child : ReadAction.compute(() -> profile.children()))
            findUsagesBelow(child);
    }

}
