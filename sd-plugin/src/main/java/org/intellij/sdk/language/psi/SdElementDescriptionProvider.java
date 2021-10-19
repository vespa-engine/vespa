package org.intellij.sdk.language.psi;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.intellij.sdk.language.psi.SdDeclaration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class SdElementDescriptionProvider implements ElementDescriptionProvider {
    
    /**
     * Controls the headline of the element in the "Find Usages" window
     * @param psiElement the element to describe
     * @param elementDescriptionLocation 
     * @return a string with the description to write in the headline
     */
    @Nullable
    @Override
    public String getElementDescription(@NotNull PsiElement psiElement, @NotNull ElementDescriptionLocation elementDescriptionLocation) {
        if (psiElement instanceof SdDeclaration) {
            return ((SdDeclaration) psiElement).getTypeName();
        } else {
            return "";
        }
    }
}
