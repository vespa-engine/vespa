// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

public class SdCodeStyleSettings extends CustomCodeStyleSettings {
    
    public SdCodeStyleSettings(CodeStyleSettings settings) {
        super("SdCodeStyleSettings", settings);
    }
    
}
