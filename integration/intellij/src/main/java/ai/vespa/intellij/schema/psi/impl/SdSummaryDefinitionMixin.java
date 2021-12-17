// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import ai.vespa.intellij.schema.SdIcons;
import ai.vespa.intellij.schema.psi.SdTypes;

import javax.swing.Icon;

/**
 * This class is used for methods' implementations for SdSummaryDefinition. Connected with "mixin" to SummaryDefinition
 * rule in sd.bnf
 *
 * @author Shahar Ariel
 */
public abstract class SdSummaryDefinitionMixin extends ASTWrapperPsiElement {
    
    public SdSummaryDefinitionMixin(ASTNode node) {
        super(node);
    }
    
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
