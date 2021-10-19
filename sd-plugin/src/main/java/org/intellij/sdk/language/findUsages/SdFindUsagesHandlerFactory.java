package org.intellij.sdk.language.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SdFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
    
    @Override
    public boolean canFindUsages(@NotNull PsiElement element) {
        return element instanceof PsiNamedElement;
    }
    
    @Override
    public @Nullable FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element,
                                                               boolean forHighlightUsages) {
        return new SdFindUsagesHandler(element);
    }
}
