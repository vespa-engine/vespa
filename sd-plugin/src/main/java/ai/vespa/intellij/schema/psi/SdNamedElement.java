// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.psi;

import com.intellij.psi.PsiNameIdentifierOwner;

/**
 * This interface is used to wrap a Psi Element with SdNamedElement interface, which enables the element to be a
 * "name owner" (like an identifier). It allows the element to take a part in references, find usages and more.
 *
 * @author Shahar Ariel
 */
public interface SdNamedElement extends PsiNameIdentifierOwner {
    
}
