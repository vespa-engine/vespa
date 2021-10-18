package org.intellij.sdk.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

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
