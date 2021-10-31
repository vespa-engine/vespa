package org.intellij.sdk.language.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import org.intellij.sdk.language.SdReference;
import org.intellij.sdk.language.psi.SdIdentifier;
import org.jetbrains.annotations.NotNull;

/**
 * This class is used for methods' implementations for SdIdentifier. The abstract class SdIdentifierMixin extents it.
 * @author Shahar Ariel
 */
public class SdIdentifierMixinImpl extends ASTWrapperPsiElement implements SdIdentifier {
    
    public SdIdentifierMixinImpl(@NotNull ASTNode node) {
        super(node);
    }
    
    @NotNull
    public PsiReference getReference() {
        return new SdReference(this, new TextRange(0, getNode().getText().length()));
    }
}
