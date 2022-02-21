// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema;

import com.intellij.openapi.fileTypes.LanguageFileType;

import javax.swing.Icon;

/**
 * This class is used for the extension (in plugin.xml), to define SD as a file's type.
 *
 * @author Shahar Ariel
 */
public class SdFileType extends LanguageFileType {
    
    public static final SdFileType INSTANCE = new SdFileType();
    
    private SdFileType() {
        super(SdLanguage.INSTANCE);
    }
    
    @Override
    public String getName() {
        return "Sd File";
    }
    
    @Override
    public String getDescription() {
        return "A Vespa schema file";
    }
    
    @Override
    public String getDefaultExtension() {
        return "sd";
    }
    
    @Override
    public Icon getIcon() {
        return SdIcons.FILE;
    }
    
}
