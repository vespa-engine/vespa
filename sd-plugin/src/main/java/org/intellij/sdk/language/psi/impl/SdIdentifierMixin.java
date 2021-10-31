package org.intellij.sdk.language.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.intellij.sdk.language.psi.SdElementFactory;
import org.intellij.sdk.language.psi.SdIdentifier;
import org.intellij.sdk.language.psi.SdIdentifierVal;
import org.jetbrains.annotations.NotNull;

/**
 * This abstract class is used for methods' implementations for SdIdentifier. Connected with "mixin" to IdentifierVal and 
 * IdentifierWithDashVal rules in sd.bnf
 * @author Shahar Ariel
 */
public abstract class SdIdentifierMixin extends SdIdentifierMixinImpl implements PsiNamedElement {
    
    public SdIdentifierMixin(@NotNull ASTNode node) {
        super(node);
    }
    
    @NotNull
    public String getName() {
        // IMPORTANT: Convert embedded escaped spaces to simple spaces
        return this.getText().replaceAll("\\\\ ", " ");
    }
    
    @NotNull
    public PsiElement setName(@NotNull String newName) {
        ASTNode node =  this.getNode().getFirstChildNode();
        if (node != null) {
            SdIdentifier elementName;
            if (this instanceof SdIdentifierVal) {
                elementName = SdElementFactory.createIdentifierVal(this.getProject(), newName);
            } else { // this instanceof SdIdentifierWithDashVal
                elementName = SdElementFactory.createIdentifierWithDashVal(this.getProject(), newName);
            }
            ASTNode newNode = elementName.getFirstChild().getNode();
            this.getNode().replaceChild(node, newNode);
        }
        return this;
    }
    

    
}
