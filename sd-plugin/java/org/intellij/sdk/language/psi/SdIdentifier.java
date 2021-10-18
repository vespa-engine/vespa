package org.intellij.sdk.language.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

public interface SdIdentifier extends PsiElement {
    
    String getName();
    
    boolean isFunctionName(PsiElement file);
    
    @NotNull PsiReference getReference();
    
}
