// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.hierarchy;

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;
import ai.vespa.intellij.schema.psi.SdIdentifierVal;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;

/**
 * This class is used for the extension (in plugin.xml), to enable "Call Hierarchy" window using the plugin code.
 *
 * @author Shahar Ariel
 */
public class SdCallHierarchyProvider implements HierarchyProvider {

    @Override
    public PsiElement getTarget(DataContext dataContext) {
        final Project project = CommonDataKeys.PROJECT.getData(dataContext);
        final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        if (project == null || editor == null) return null;

        final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file == null) {
            return null;
        }
        final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        if (element == null) {
            return null;
        }

        if (element instanceof SdIdentifierVal || element.getParent() instanceof SdIdentifierVal) {
            PsiReference ref = element instanceof SdIdentifierVal ? element.getReference() : element.getParent().getReference();
            if (ref == null) {
                return null;
            }
            PsiElement resolvedRef = ref.resolve();
            if (resolvedRef instanceof SdFunctionDefinition) {
                return resolvedRef;
            }
        }
        return null;
    }

    @Override
    public HierarchyBrowser createHierarchyBrowser(PsiElement target) {
        return new SdCallHierarchyBrowser(target.getProject(), target);
    }

    @Override
    public void browserActivated(HierarchyBrowser hierarchyBrowser) {
        ((SdCallHierarchyBrowser) hierarchyBrowser).changeView(CallHierarchyBrowserBase.getCallerType());
    }

}
