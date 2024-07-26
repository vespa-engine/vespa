package ai.vespa.schemals.intellij

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringHelper
import com.intellij.usageView.UsageInfo

class SchemaRefactoringHelper : RefactoringHelper<Collection<String>> {
    override fun prepareOperation(p0: Array<out UsageInfo>, p1: MutableList<PsiElement>): Collection<String> {
        println("Prepare operation")
        return emptyList();
    }

    override fun performOperation(p0: Project, p1: Collection<String>?) {
        println("Perform operation")
    }
}