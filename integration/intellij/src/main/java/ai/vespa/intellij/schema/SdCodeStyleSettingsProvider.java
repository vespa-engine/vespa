// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.psi.codeStyle.CodeStyleConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is used for the extension (in plugin.xml) to the class SdCodeStyleSettings.
 *
 * @author Shahar Ariel
 */
public class SdCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
    
    @Override
    public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
        return new SdCodeStyleSettings(settings);
    }
    
    @Nullable
    @Override
    public String getConfigurableDisplayName() {
        return "Sd";
    }
    
    @NotNull
    public CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings modelSettings) {
        return new CodeStyleAbstractConfigurable(settings, modelSettings, this.getConfigurableDisplayName()) {
            @Override
            protected CodeStyleAbstractPanel createPanel(CodeStyleSettings settings) {
                return new SdCodeStyleMainPanel(getCurrentSettings(), settings);
            }
        };
    }
    
    private static class SdCodeStyleMainPanel extends TabbedLanguageCodeStylePanel {
        
        public SdCodeStyleMainPanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
            super(SdLanguage.INSTANCE, currentSettings, settings);
        }
        
    }
    
}
