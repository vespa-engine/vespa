// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import ai.vespa.intellij.schema.psi.SdElementFactory;
import ai.vespa.intellij.schema.psi.SdIdentifier;
import ai.vespa.intellij.schema.psi.SdIdentifierVal;

/**
 * This abstract class is used for methods' implementations for SdIdentifier. Connected with "mixin" to IdentifierVal and 
 * IdentifierWithDashVal rules in sd.bnf
 *
 * @author Shahar Ariel
 */
public abstract class SdIdentifierMixin extends SdIdentifierMixinImpl implements PsiNamedElement {
    
    public SdIdentifierMixin(ASTNode node) {
        super(node);
    }
    
    public String getName() {
        // IMPORTANT: Convert embedded escaped spaces to simple spaces
        return this.getText().replaceAll("\\\\ ", " ");
    }
    
    public PsiElement setName(String newName) {
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
