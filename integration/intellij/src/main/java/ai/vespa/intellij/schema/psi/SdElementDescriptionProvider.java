// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.psi;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import ai.vespa.intellij.schema.psi.impl.SdNamedElementImpl;

/**
 * This class is used for the extension (in plugin.xml), to enable "find Usages" window take the element description from 
 * here. Used only for the "target" element.
 *
 * @author Shahar Ariel
 */
public class SdElementDescriptionProvider implements ElementDescriptionProvider {
    
    /**
     * Controls the headline of the element in the "Find Usages" window.
     *
     * @param psiElement the element to describe
     * @return a string with the description to write in the headline
     */
    @Override
    public String getElementDescription(PsiElement psiElement, ElementDescriptionLocation elementDescriptionLocation) {
        if (psiElement instanceof SdDeclaration) {
            return ((SdNamedElementImpl) psiElement).getTypeName();
        } else {
            return "";
        }
    }

}
