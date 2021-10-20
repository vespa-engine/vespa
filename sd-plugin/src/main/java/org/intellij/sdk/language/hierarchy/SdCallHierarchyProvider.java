// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.hierarchy;


import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.intellij.sdk.language.SdReference;
import org.intellij.sdk.language.SdUtil;
import org.intellij.sdk.language.psi.SdDeclaration;
import org.intellij.sdk.language.psi.SdFunctionDefinition;
import org.intellij.sdk.language.psi.SdIdentifierVal;
import org.intellij.sdk.language.psi.impl.SdPsiImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;

import com.intellij.psi.util.PsiTreeUtil;

/**
 * This class is used for the extension (in plugin.xml), to enable "Call Hierarchy" window using the plugin code.
 * @author shahariel
 */
public class SdCallHierarchyProvider implements HierarchyProvider {

    @Override
    public @Nullable PsiElement getTarget(@NotNull DataContext dataContext) {
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
    public @NotNull HierarchyBrowser createHierarchyBrowser(@NotNull PsiElement target) {
        return new SdCallHierarchyBrowser(target.getProject(), target);
    }

    @Override
    public void browserActivated(@NotNull HierarchyBrowser hierarchyBrowser) {
        ((SdCallHierarchyBrowser) hierarchyBrowser).changeView(CallHierarchyBrowserBase.getCallerType());
    }
}
