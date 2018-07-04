// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class LocaleFactoryTestCase {

    @Test
    public void requireThatLocaleCanBeCreatedFromLanguageTag() {
        assertLocale("zh", "zh", "", "");
        assertLocale("zh-CN", "zh", "", "CN");
        assertLocale("zh-foo-CN", "zh", "", "CN");
        assertLocale("zh-Hans", "zh", "Hans", "");
        assertLocale("zh-TW", "zh", "", "TW");
        assertLocale("zh-foo-TW", "zh", "", "TW");
        assertLocale("zh-Hant", "zh", "Hant", "");
        assertLocale("ja", "ja", "", "");
        assertLocale("ko", "ko", "", "");
        assertLocale("en", "en", "", "");
        assertLocale("en-NO", "en", "", "NO");
        assertLocale("de", "de", "", "");
        assertLocale("es", "es", "", "");
        assertLocale("es-419", "es", "", "419");

        try {
            LocaleFactory.fromLanguageTag(null);
            fail();
        } catch (NullPointerException e) {

        }

        assertLocale("", "", "", "");
        assertLocale("z-foo", "", "", "");
        assertLocale("ojeroierhoiherohjdadsfodsfoifiopeoipefwoipfwe", "", "", "");
    }

    private static void assertLocale(String tag, String language, String variant, String country) {
        Locale locale = LocaleFactory.fromLanguageTag(tag);
        assertEquals(language, locale.getLanguage());
        assertEquals(country, locale.getCountry());
        assertEquals(variant, locale.getVariant());
    }

}
