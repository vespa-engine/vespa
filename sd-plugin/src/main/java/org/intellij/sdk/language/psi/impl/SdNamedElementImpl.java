package org.intellij.sdk.language.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.intellij.sdk.language.psi.SdNamedElement;
import org.jetbrains.annotations.NotNull;

public abstract class SdNamedElementImpl extends ASTWrapperPsiElement implements SdNamedElement {
    
    public SdNamedElementImpl(@NotNull ASTNode node) {
        super(node);
    }
    
}
