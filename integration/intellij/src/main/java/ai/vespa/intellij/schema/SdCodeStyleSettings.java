// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

/**
 * This class represent a code style settings, and creates an option page in settings/preferences.
 *
 * @author Shahar Ariel
 */
public class SdCodeStyleSettings extends CustomCodeStyleSettings {
    
    public SdCodeStyleSettings(CodeStyleSettings settings) {
        super("SdCodeStyleSettings", settings);
    }
    
}
