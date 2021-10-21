// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language;

import com.intellij.lang.Language;

/**
 * This class defines SD as a language.
 * @author shahariel
 */
public class SdLanguage extends Language {
    public static final SdLanguage INSTANCE = new SdLanguage();
    
    private SdLanguage() {
        super("Sd");
    }
    
    @Override
    public boolean isCaseSensitive() {
        return true;
    }
}
