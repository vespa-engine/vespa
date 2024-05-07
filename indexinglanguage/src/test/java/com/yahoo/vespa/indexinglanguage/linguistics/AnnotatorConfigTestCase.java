// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.linguistics;

import com.yahoo.language.Language;
import com.yahoo.language.process.StemMode;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class AnnotatorConfigTestCase {

    @Test
    public void requireThatAccessorsWork() {
        AnnotatorConfig config = new AnnotatorConfig();
        for (Language language : Language.values()) {
            config.setLanguage(language);
            assertEquals(language, config.getLanguage());
        }
        for (StemMode mode : StemMode.values()) {
            config.setStemMode(mode);
            assertEquals(mode, config.getStemMode());
        }
        config.setRemoveAccents(true);
        assertTrue(config.getRemoveAccents());
        config.setRemoveAccents(false);
        assertFalse(config.getRemoveAccents());
        config.setMaxTokenLength(10);
        assertEquals(10, config.getMaxTokenLength());
    }

    @Test
    public void requireThatCopyConstructorIsImplemented() {
        AnnotatorConfig config = new AnnotatorConfig();
        config.setLanguage(Language.ARABIC);
        config.setStemMode(StemMode.SHORTEST);
        config.setRemoveAccents(!config.getRemoveAccents());
        config.setMaxTokenLength(11);

        AnnotatorConfig other = new AnnotatorConfig(config);
        assertEquals(config.getLanguage(), other.getLanguage());
        assertEquals(config.getStemMode(), other.getStemMode());
        assertEquals(config.getRemoveAccents(), other.getRemoveAccents());
        assertEquals(config.getMaxTokenLength(), other.getMaxTokenLength());
    }

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        AnnotatorConfig config = newConfig(Language.DUTCH, StemMode.NONE, true);
        assertFalse(config.equals(new Object()));
        assertFalse(config.equals(newConfig(Language.SPANISH, StemMode.SHORTEST, false)));
        assertFalse(config.equals(newConfig(Language.DUTCH, StemMode.SHORTEST, false)));
        assertFalse(config.equals(newConfig(Language.DUTCH, StemMode.NONE, false)));
        assertNotEquals(config, newConfig(Language.DUTCH, StemMode.NONE, true).setMaxTokenLength(10));
        assertEquals(config, newConfig(Language.DUTCH, StemMode.NONE, true));
        assertEquals(config.hashCode(), newConfig(Language.DUTCH, StemMode.NONE, true).hashCode());
    }

    private static AnnotatorConfig newConfig(Language language, StemMode stemMode,
                                             boolean removeAccents) {
        AnnotatorConfig config = new AnnotatorConfig();
        config.setLanguage(language);
        config.setStemMode(stemMode);
        config.setRemoveAccents(removeAccents);
        return config;
    }
}
