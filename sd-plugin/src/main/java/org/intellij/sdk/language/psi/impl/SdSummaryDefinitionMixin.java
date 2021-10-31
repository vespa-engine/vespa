package org.intellij.sdk.language.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import org.intellij.sdk.language.SdIcons;
import org.intellij.sdk.language.psi.SdTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * This class is used for methods' implementations for SdSummaryDefinition. Connected with "mixin" to SummaryDefinition
 * rule in sd.bnf
 * @author Shahar Ariel
 */
public abstract class SdSummaryDefinitionMixin extends ASTWrapperPsiElement {
    
    public SdSummaryDefinitionMixin(@NotNull ASTNode node) {
        super(node);
    }
    
    @NotNull
    public String getName() {
        ASTNode node;
        node = this.getNode().findChildByType(SdTypes.IDENTIFIER_WITH_DASH_VAL);
        if (node != null) {
            return node.getText();
        } else {
            return "";
        }
    }
    
    @Override
    public ItemPresentation getPresentation() {
        final SdSummaryDefinitionMixin element = this;
        return new ItemPresentation() {
            
            @Override
            public String getPresentableText() {
                return getName();
            }
            
            @Nullable
            @Override
            public String getLocationString() {
                return element.getContainingFile() != null ? element.getContainingFile().getName() : null;
            }
            
            @Override
            public Icon getIcon(boolean unused) {
                return SdIcons.SUMMARY;
            }
        };
    }
}
