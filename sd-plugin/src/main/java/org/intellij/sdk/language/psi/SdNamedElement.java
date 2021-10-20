package org.intellij.sdk.language.psi;

import com.intellij.psi.PsiNameIdentifierOwner;

/**
 * This interface is used to wrap a Psi Element with SdNamedElement interface, which enables the element to be a
 * "name owner" (like an identifier). It allows the element to take a part in references, find usages and more.
 * @author shahariel
 */
public interface SdNamedElement extends PsiNameIdentifierOwner {
    
}
