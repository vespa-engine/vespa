// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.util.IncorrectOperationException;
import org.intellij.sdk.language.psi.SdDeclaration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * This class represent a reference to a Psi Element.
 * @author Shahar Ariel
 */
public class SdReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {
    
    private final String elementName;
    
    public SdReference(@NotNull PsiElement element, TextRange textRange) {
        super(element, textRange);
        elementName = element.getText().substring(textRange.getStartOffset(), textRange.getEndOffset());
    }
    
    @NotNull
    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        
        PsiElement file = myElement.getContainingFile();
        
        final List<SdDeclaration> declarations = SdUtil.findDeclarationsByScope(file, myElement, elementName);
        
        List<ResolveResult> results = new ArrayList<>();
        
        for (SdDeclaration declaration : declarations) {
            results.add(new PsiElementResolveResult(declaration));
        }
        return results.toArray(new ResolveResult[results.size()]);
    }
    
    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }
    
    @Override
    public Object @NotNull [] getVariants() {
        List<SdDeclaration> declarations = SdUtil.findDeclarations(myElement.getContainingFile());
        List<LookupElement> variants = new ArrayList<>();
        for (final SdDeclaration element : declarations) {
            if (element.getName() != null && element.getName().length() > 0) {
                variants.add(LookupElementBuilder
                                 .create(element).withIcon(SdIcons.FILE)
                                 .withTypeText(element.getContainingFile().getName())
                );
            }
        }
        return variants.toArray();
    }
    
    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        return ((PsiNamedElement) myElement).setName(newElementName);
    }
    
}
