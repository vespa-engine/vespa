// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.hierarchy;

import ai.vespa.intellij.schema.model.RankProfile;
import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import ai.vespa.intellij.schema.psi.SdFile;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Call Hierarchy feature utilities.
 *
 * @author Shahar Ariel
 * @author bratseth
 */
public class SdHierarchyUtil {
    
    private static final Comparator<NodeDescriptor<?>> NODE_DESCRIPTOR_COMPARATOR = Comparator.comparingInt(NodeDescriptor::getIndex);
    
    private SdHierarchyUtil() {
    }
    
    public static boolean isExecutable(PsiElement component) {
        return component instanceof SdFunctionDefinition;
    }

    public static Set<SdRankProfileDefinition> getRankProfileChildren(SdFile file, RankProfile targetProfile) {
        return PsiTreeUtil.collectElementsOfType(file, SdRankProfileDefinition.class)
                          .stream()
                          .filter(profile -> isChildOf(targetProfile, new RankProfile(profile, null)))
                          .collect(Collectors.toSet());
    }

    private static boolean isChildOf(RankProfile targetProfile, RankProfile thisProfile) {

        if (Objects.equals(thisProfile.name(), targetProfile.name())) return true;
        return thisProfile.parents().values()
                          .stream()
                          .anyMatch(parent -> isChildOf(targetProfile, parent));
    }

    public static Comparator<NodeDescriptor<?>> getComparator(Project project) {
        final HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(project).getState();
        if (state != null && state.SORT_ALPHABETICALLY) {
            return AlphaComparator.INSTANCE;
        }
        else {
            return NODE_DESCRIPTOR_COMPARATOR;
        }
    }
    
}
