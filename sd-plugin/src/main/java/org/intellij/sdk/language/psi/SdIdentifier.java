// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

public interface SdIdentifier extends PsiElement {
    
    String getName();
    
    boolean isFunctionName(PsiElement file);
    
    @NotNull PsiReference getReference();
    
}
