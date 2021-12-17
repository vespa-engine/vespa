// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import ai.vespa.intellij.schema.psi.SdIdentifier;

/**
 * This class is used for the extension (in plugin.xml), to enable refactoring.
 *
 * @author Shahar Ariel
 */
public class SdRefactoringSupportProvider extends RefactoringSupportProvider {

    @Override
    public boolean isMemberInplaceRenameAvailable(PsiElement elementToRename, PsiElement context) {
        return (elementToRename instanceof SdIdentifier);
    }

}
