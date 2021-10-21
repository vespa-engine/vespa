// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.hierarchy;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import org.intellij.sdk.language.SdIcons;
import org.intellij.sdk.language.SdUtil;
import org.intellij.sdk.language.psi.SdFirstPhaseDefinition;
import org.intellij.sdk.language.psi.SdFunctionDefinition;

import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * This class represents a node descriptor to a node in a tree in the "Call Hierarchy" window.
 * @author shahariel
 */
public class SdCallHierarchyNodeDescriptor extends HierarchyNodeDescriptor {

    public SdCallHierarchyNodeDescriptor(final NodeDescriptor parentDescriptor, @NotNull final PsiElement element, final boolean isBase) {
        super(element.getProject(), parentDescriptor, element, isBase);
        CompositeAppearance.DequeEnd beginning = myHighlightedText.getBeginning();
        if (element instanceof SdFunctionDefinition) {
            beginning.addText(SdUtil.createFunctionDescription((SdFunctionDefinition) element));
        } else if (element instanceof SdFirstPhaseDefinition) {
            beginning.addText(((SdFirstPhaseDefinition) element).getName());
        } else {
            beginning.addText(element.getText());
        }
    }
    
    @Override
    public boolean update() {
        boolean changes = super.update();
        final CompositeAppearance oldText = myHighlightedText;
        myHighlightedText = new CompositeAppearance();
        NavigatablePsiElement element = (NavigatablePsiElement)getPsiElement();
        if (element == null) {
            return invalidElement();
        }
        
        final ItemPresentation presentation = element.getPresentation();
        if (presentation != null) {
            myHighlightedText.getEnding().addText(presentation.getPresentableText());
            PsiFile file = element.getContainingFile();
            if (file != null) { // adds the file's name
                myHighlightedText.getEnding().addText(" (" + file.getName() + ")", HierarchyNodeDescriptor.getPackageNameAttributes());
            }
            Icon icon = SdIcons.FILE;
            if (element instanceof SdFunctionDefinition) {
                icon = ((SdFunctionDefinition) element).isOverride() ? SdIcons.OVERRIDE_MACRO : SdIcons.MACRO;
            } else if (element instanceof SdFirstPhaseDefinition) {
                icon = SdIcons.FIRST_PHASE;
            }
            installIcon(icon, changes);
        }
        
        myName = myHighlightedText.getText();
        if (!Comparing.equal(myHighlightedText, oldText)) {
            changes = true;
        }
        
        
        return changes;

    }
    
}
