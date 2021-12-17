// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.util.PsiTreeUtil;
import ai.vespa.intellij.schema.SdIcons;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;

import javax.swing.Icon;

/**
 * This class is used for methods' implementations for SdFirstPhaseDefinition. Connected with "mixin" to 
 * FirstPhaseDefinition rule in sd.bnf
 *
 * @author Shahar Ariel
 */
public class SdFirstPhaseDefinitionMixin extends ASTWrapperPsiElement {
    
    public SdFirstPhaseDefinitionMixin(ASTNode node) {
        super(node);
    }
    
    public String getName() {
        SdRankProfileDefinition rankProfile = PsiTreeUtil.getParentOfType(this, SdRankProfileDefinition.class);
        if (rankProfile == null) {
            return "";
        }
        return "first-phase of " + rankProfile.getName();
    }
    
    public ItemPresentation getPresentation() {
        final SdFirstPhaseDefinitionMixin element = this;
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                SdRankProfileDefinition rankProfile = PsiTreeUtil.getParentOfType(element, SdRankProfileDefinition.class);
                if (rankProfile == null) {
                    return "";
                }
                return "first-phase of " + rankProfile.getName();
            }
            
            @Override
            public String getLocationString() {
                return element.getContainingFile() != null ? element.getContainingFile().getName() : null;
            }
            
            @Override
            public Icon getIcon(boolean unused) {
                return SdIcons.FIRST_PHASE;
            }
        };
    }
}

