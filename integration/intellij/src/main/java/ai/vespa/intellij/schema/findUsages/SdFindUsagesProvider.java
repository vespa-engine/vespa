// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.findUsages;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import ai.vespa.intellij.schema.lexer.SdLexerAdapter;
import ai.vespa.intellij.schema.psi.SdDeclaration;
import ai.vespa.intellij.schema.psi.SdIdentifierVal;
import ai.vespa.intellij.schema.psi.SdIdentifierWithDashVal;
import ai.vespa.intellij.schema.psi.SdTypes;
import ai.vespa.intellij.schema.psi.impl.SdNamedElementImpl;

/**
 * This class is used for the extension (in plugin.xml), to enable "find Usages" window using the plugin code.
 *
 * @author Shahar Ariel
 */
public class SdFindUsagesProvider implements FindUsagesProvider {

    public WordsScanner getWordsScanner() {
        // TODO: Not used at the moment (?) as we search by brute force
        return new DefaultWordsScanner(new SdLexerAdapter(),
                                       TokenSet.create(SdTypes.ID_REG,
                                                       SdTypes.IDENTIFIER_VAL,
                                                       SdTypes.IDENTIFIER_WITH_DASH_VAL),
                                       TokenSet.create(SdTypes.COMMENT),
                                       TokenSet.create(SdTypes.STRING_REG, SdTypes.INTEGER_REG, SdTypes.FLOAT_REG));
    }

    @Override
    public boolean canFindUsagesFor(PsiElement psiElement) {
        return psiElement instanceof PsiNamedElement;
    }

    @Override
    public String getHelpId(PsiElement psiElement) {
        return null;
    }

    @Override
    public String getType(PsiElement element) {
        if (element instanceof SdDeclaration) {
            return ((SdNamedElementImpl) element).getTypeName();
        } else {
            return "";
        }
    }

    @Override
    public String getDescriptiveName(PsiElement element) {
        return "";
    }

    @Override
    public String getNodeText(PsiElement element, boolean useFullName) {
        if (element instanceof SdIdentifierVal || element instanceof SdIdentifierWithDashVal) {
            String name = ((PsiNamedElement) element).getName();
            return name != null ? name : "";
        } else if (element instanceof SdDeclaration) {
            String fullText = element.getNode().getText();
            return fullText.substring(0, fullText.indexOf('{'));
        } else {
            return "";
        }
    }

}
