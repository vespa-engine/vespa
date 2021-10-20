package org.intellij.sdk.language.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

/**
 * This interface represents an identifier in the SD language (regular identifiers and identifiers with dash).
 * @author shahariel
 */
public interface SdIdentifier extends PsiElement {
    
    String getName();
    
    boolean isFunctionName(PsiElement file);
    
    @NotNull PsiReference getReference();
    
}
