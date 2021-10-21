// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.intellij.sdk.language.psi.SdNamedElement;
import org.jetbrains.annotations.NotNull;

/**
 * This abstract class is used to wrap a Psi Element with SdNamedElement interface, which enables the element to be a
 * "name owner" (like an identifier). It allows the element to take a part in references, find usages and more.
 * @author shahariel
 */
public abstract class SdNamedElementImpl extends ASTWrapperPsiElement implements SdNamedElement {
    
    public SdNamedElementImpl(@NotNull ASTNode node) {
        super(node);
    }
    
}
