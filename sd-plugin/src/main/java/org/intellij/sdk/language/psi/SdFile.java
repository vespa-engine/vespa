// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.intellij.sdk.language.SdFileType;
import org.intellij.sdk.language.SdLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * This class represent an SD file.
 * @author Shahar Ariel
 */
public class SdFile extends PsiFileBase {
    
    public SdFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, SdLanguage.INSTANCE);
    }
    
    @NotNull
    @Override
    public FileType getFileType() {
        return SdFileType.INSTANCE;
    }
    
    @Override
    public String toString() {
        return "Sd File";
    }
}
