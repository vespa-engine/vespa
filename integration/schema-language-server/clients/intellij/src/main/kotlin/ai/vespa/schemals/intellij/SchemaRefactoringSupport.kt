package ai.vespa.schemals.intellij

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement

class SchemaRefactoringSupport : RefactoringSupportProvider() {

    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?) = true;
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?) = true;
}