// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * This class is used for the extension (in plugin.xml), to define SD as a file's type.
 * @author shahariel
 */
public class SdFileType extends LanguageFileType {
    
    public static final SdFileType INSTANCE = new SdFileType();
    
    private SdFileType() {
        super(SdLanguage.INSTANCE);
    }
    
    @NotNull
    @Override
    public String getName() {
        return "Sd File";
    }
    
    @NotNull
    @Override
    public String getDescription() {
        return "Sd language file";
    }
    
    @NotNull
    @Override
    public String getDefaultExtension() {
        return "sd";
    }
    
    @Nullable
    @Override
    public Icon getIcon() {
        return SdIcons.FILE;
    }
    
}
