// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import ai.vespa.intellij.schema.SdReference;
import ai.vespa.intellij.schema.psi.SdIdentifier;

/**
 * This class is used for methods' implementations for SdIdentifier. The abstract class SdIdentifierMixin extents it.
 *
 * @author Shahar Ariel
 */
public class SdIdentifierMixinImpl extends ASTWrapperPsiElement implements SdIdentifier {
    
    public SdIdentifierMixinImpl(ASTNode node) {
        super(node);
    }
    
    public PsiReference getReference() {
        return new SdReference(this, new TextRange(0, getNode().getText().length()));
    }

}
