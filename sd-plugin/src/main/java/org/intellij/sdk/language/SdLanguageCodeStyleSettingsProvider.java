// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language;

import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;

/**
 * This class is used for the extension (in plugin.xml), to make the IDE use our plugin's code for coding style.
 * @author Shahar Ariel
 */
public class SdLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
    
    @NotNull
    @Override
    public Language getLanguage() {
        return SdLanguage.INSTANCE;
    }
    
    @Override
    public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
        if (settingsType == SettingsType.SPACING_SETTINGS) {
            consumer.showStandardOptions("SPACE_AFTER_COLON");
//            consumer.renameStandardOption("SPACE_AROUND_ASSIGNMENT_OPERATORS", "Separator");
        } else if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
            consumer.showStandardOptions("KEEP_BLANK_LINES_IN_CODE");
//        } else if (settingsType == SettingsType.INDENT_SETTINGS) {
//            consumer.showStandardOptions("USE_RELATIVE_INDENTS");
        }
    }
    
    @Override
    public String getCodeSample(@NotNull SettingsType settingsType) {
        return "field myField type int {\n    indexing: summary\n}";
    }
    
}
