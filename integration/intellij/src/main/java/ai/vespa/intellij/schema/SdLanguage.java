// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema;

import com.intellij.lang.Language;

/**
 * This class defines SD as a language.
 *
 * @author Shahar Ariel
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
