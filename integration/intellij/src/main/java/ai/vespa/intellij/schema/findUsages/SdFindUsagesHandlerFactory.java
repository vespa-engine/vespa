// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

/**
 * This class is used for the extension (in plugin.xml) to the class SdFindUsagesHandler.
 *
 * @author Shahar Ariel
 */
public class SdFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
    
    @Override
    public boolean canFindUsages(PsiElement element) {
        return element instanceof PsiNamedElement;
    }
    
    @Override
    public FindUsagesHandler createFindUsagesHandler(PsiElement element, boolean forHighlightUsages) {
        return new SdFindUsagesHandler(element);
    }

}
