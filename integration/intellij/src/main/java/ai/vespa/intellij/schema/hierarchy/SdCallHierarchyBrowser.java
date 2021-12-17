// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.hierarchy;

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;

import java.util.Comparator;
import java.util.Map;

import javax.swing.JTree;

/**
 * A browser for the "Call Hierarchy" window.
 *
 * @author Shahar Ariel
 */
public class SdCallHierarchyBrowser extends CallHierarchyBrowserBase {

    public SdCallHierarchyBrowser(Project project, PsiElement macro) {
        super(project, macro);
    }

    @Override
    protected PsiElement getElementFromDescriptor(HierarchyNodeDescriptor descriptor) {
        return ObjectUtils.tryCast(descriptor.getPsiElement(), PsiElement.class);
    }

    @Override
    protected void createTrees(Map<? super String, ? super JTree> type2TreeMap) {
        ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction(IdeActions.GROUP_CALL_HIERARCHY_POPUP);
        type2TreeMap.put(getCallerType(), createHierarchyTree(group));
        type2TreeMap.put(getCalleeType(), createHierarchyTree(group));
    }

    private JTree createHierarchyTree(ActionGroup group) {
        return createTree(false);
    }

    @Override
    protected boolean isApplicableElement(PsiElement element) {
        return element instanceof SdFunctionDefinition;
    }
    
    @Override
    protected HierarchyTreeStructure createHierarchyTreeStructure(String typeName, PsiElement psiElement) {
        if (getCallerType().equals(typeName)) {
            return new SdCallerTreeStructure(myProject, psiElement, getCurrentScopeType());
        }
        else if (getCalleeType().equals(typeName)) {
            return new SdCalleeTreeStructure(myProject, psiElement, getCurrentScopeType());
        }
        else {
            return null;
        }
    }

    @Override
    protected Comparator<NodeDescriptor<?>> getComparator() {
        return SdHierarchyUtil.getComparator(myProject);
    }

}
