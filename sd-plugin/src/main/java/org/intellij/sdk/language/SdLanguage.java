package org.intellij.sdk.language;

import com.intellij.lang.Language;

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
