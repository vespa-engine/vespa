// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.model;

import ai.vespa.intellij.schema.psi.SdFile;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import ai.vespa.intellij.schema.psi.SdSchemaBody;
import ai.vespa.intellij.schema.psi.SdSchemaDefinition;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Optional;

/**
 * A schema.
 *
 * @author bratseth
 */
public class Schema {

    private final SdFile definition;

    public Schema(SdFile definition) {
        this.definition = definition;
    }

    public SdFile definition() { return definition; }

    /**
     * Returns the profile of the given name from the given file.
     *
     * @throws IllegalArgumentException if not found
     */
    public static Schema fromProjectFile(Project project, String filePath) {
        return new Schema((SdFile)load(project, filePath));
    }

    static PsiElement load(Project project, String filePath) {
        PsiFile[] psiFile = FilenameIndex.getFilesByName(project, filePath, GlobalSearchScope.allScope(project));
        if (psiFile.length == 0)
            throw new IllegalArgumentException(filePath + " could not be opened");
        if (psiFile.length > 1)
            throw new IllegalArgumentException("Multiple files matches " + filePath);
        if ( ! (psiFile[0] instanceof SdFile))
            throw new IllegalArgumentException(filePath + " is not a schema file");
        return psiFile[0];
    }

}
