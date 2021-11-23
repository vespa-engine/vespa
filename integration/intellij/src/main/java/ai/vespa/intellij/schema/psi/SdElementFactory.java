// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFileFactory;

import com.intellij.psi.util.PsiTreeUtil;
import ai.vespa.intellij.schema.SdFileType;

/**
 * This class is a factory of psi elements in the SD PSI tree.
 *
 * @author Shahar Ariel
 */
public class SdElementFactory {
    
    private static final String GENERAL_FILE_TEXT = "search {document %s {} rank-profile %s {}}";
    
    public static SdIdentifierVal createIdentifierVal(Project project, String name) {
        String fileText = String.format(GENERAL_FILE_TEXT, name, name);
        final SdFile file = createFile(project, fileText);
        return PsiTreeUtil.findChildOfType(file, SdIdentifierVal.class);
    }
    
    public static SdIdentifierWithDashVal createIdentifierWithDashVal(Project project, String name) {
        String fileText = String.format(GENERAL_FILE_TEXT, name, name);
        final SdFile file = createFile(project, fileText);
        return PsiTreeUtil.findChildOfType(file, SdIdentifierWithDashVal.class);
    }
    
    public static SdFile createFile(Project project, String text) {
        String name = "dummy.sd";
        return (SdFile) PsiFileFactory.getInstance(project).
            createFileFromText(name, SdFileType.INSTANCE, text);
    }

}
