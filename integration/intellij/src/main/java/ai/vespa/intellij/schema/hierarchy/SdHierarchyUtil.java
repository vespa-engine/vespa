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

    public static Set<SdRankProfileDefinition> getRankProfileChildren(SdFile file, SdRankProfileDefinition targetProfile) {
        return PsiTreeUtil.collectElementsOfType(file, SdRankProfileDefinition.class)
                          .stream()
                          .filter(profile -> isChildOf(targetProfile, profile))
                          .collect(Collectors.toSet());
    }

    private static boolean isChildOf(SdRankProfileDefinition targetProfile, SdRankProfileDefinition thisProfile) {
        if (thisProfile.getName().equals(targetProfile.getName())) return true;
        return new RankProfile(thisProfile).findInherited()
                                           .stream()
                                           .anyMatch(parent -> isChildOf(targetProfile, parent.definition()));
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
