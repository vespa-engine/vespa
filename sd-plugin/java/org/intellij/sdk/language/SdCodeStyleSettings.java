package org.intellij.sdk.language;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

public class SdCodeStyleSettings extends CustomCodeStyleSettings {
    
    public SdCodeStyleSettings(CodeStyleSettings settings) {
        super("SdCodeStyleSettings", settings);
    }
    
}
