// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * This interface represents an identifier in the SD language (regular identifiers and identifiers with dash).
 * @author Shahar Ariel
 */
public interface SdIdentifier extends PsiElement {

    default boolean isFunctionName(PsiElement file, String name) {
        for (SdFunctionDefinition macro : PsiTreeUtil.collectElementsOfType(file, SdFunctionDefinition.class)) {
            if (name.equals(macro.getName())) {
                return true;
            }
        }
        return false;
    }
    
}
