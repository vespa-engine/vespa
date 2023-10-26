// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Rich Pito
 */
public class LanguageTestCase {

    @Test
    public void requireThatSpecificLanguagesAreCjk() {
        List<Language> cjk = Arrays.asList(Language.CHINESE_SIMPLIFIED,
                                           Language.CHINESE_TRADITIONAL,
                                           Language.JAPANESE,
                                           Language.KOREAN,
                                           Language.THAI);
        for (Language language : cjk) {
            assertTrue(language.toString(), language.isCjk());
        }
        for (Language language : Language.values()) {
            if (cjk.contains(language)) {
                continue;
            }
            assertFalse(language.toString(), language.isCjk());
        }
    }

    @Test
    public void requireThatLanguageTagsAreRecognized() {
        assertLanguage(Language.ARABIC, "ar");
        assertLanguage(Language.CHINESE_SIMPLIFIED, "zh-hans");
        assertLanguage(Language.CHINESE_SIMPLIFIED, "zh-Hans");
        assertLanguage(Language.CHINESE_SIMPLIFIED, "zh-foo-CN");
        assertLanguage(Language.CHINESE_SIMPLIFIED, "zh-CN");
        assertLanguage(Language.CHINESE_TRADITIONAL, "zh");
        assertLanguage(Language.CHINESE_TRADITIONAL, "zh-foo");
        assertLanguage(Language.CHINESE_TRADITIONAL, "zh-hant");
        assertLanguage(Language.CHINESE_TRADITIONAL, "zh-Hant");
        assertLanguage(Language.CHINESE_TRADITIONAL, "zh-Hant-TW");
        assertLanguage(Language.CHINESE_TRADITIONAL, "zh-Hant-HK");
        assertLanguage(Language.CHINESE_TRADITIONAL, "zh-foo-TW");
        assertLanguage(Language.CHINESE_TRADITIONAL, "zh-TW");
        assertLanguage(Language.CROATIAN, "hr");
        assertLanguage(Language.DANISH, "da");
        assertLanguage(Language.DUTCH, "nl");
        assertLanguage(Language.ENGLISH, "en");
        assertLanguage(Language.ENGLISH, "en-CA");
        assertLanguage(Language.ENGLISH, "en-GB");
        assertLanguage(Language.ENGLISH, "en-US");
        assertLanguage(Language.ENGLISH, "en-Latn-i-oed-1992");
        assertLanguage(Language.FINNISH, "fi");
        assertLanguage(Language.FRENCH, "fr");
        assertLanguage(Language.FRENCH, "fr-FR");
        assertLanguage(Language.GERMAN, "de");
        assertLanguage(Language.GERMAN, "de-DE");
        assertLanguage(Language.GREEK, "el");
        assertLanguage(Language.ITALIAN, "it");
        assertLanguage(Language.ITALIAN, "it-IT");
        assertLanguage(Language.JAPANESE, "ja");
        assertLanguage(Language.KOREAN, "ko");
        assertLanguage(Language.NORWEGIAN_BOKMAL, "no");
        assertLanguage(Language.NORWEGIAN_BOKMAL, "nb");
        assertLanguage(Language.POLISH, "pl");
        assertLanguage(Language.PORTUGUESE, "pt");
        assertLanguage(Language.ROMANIAN, "ro");
        assertLanguage(Language.RUSSIAN, "ru");
        assertLanguage(Language.SPANISH, "es");
        assertLanguage(Language.SPANISH, "es-ES");
        assertLanguage(Language.SPANISH, "es-419");
        assertLanguage(Language.SWEDISH, "sv");
        assertLanguage(Language.THAI, "th");
        assertLanguage(Language.TURKISH, "tr");
        assertLanguage(Language.VIETNAMESE, "vi");

        assertLanguage(Language.UNKNOWN, null);
        assertLanguage(Language.UNKNOWN, "");
        assertLanguage(Language.UNKNOWN, "und");
        assertLanguage(Language.UNKNOWN, "z-foo");
        assertLanguage(Language.UNKNOWN, "ojeroierhoiherohjdadsfodsfoifiopeoipefwoipfwe");
        assertLanguage(Language.UNKNOWN, "#$_^@#$_@%#$)%@$%^--@&&&#-%^_^%");
    }

    @Test
    public void requireThatLanguageIsGuessedCorrectlyFromEncodings() {
        assertSame(Language.UNKNOWN, Language.fromEncoding(null));
        assertSame(Language.UNKNOWN, Language.fromEncoding("lkij"));
        assertSame(Language.UNKNOWN, Language.fromEncoding("(/)(###)"));

        assertSame(Language.CHINESE_SIMPLIFIED, Language.fromEncoding("GB2312"));
        assertSame(Language.CHINESE_TRADITIONAL, Language.fromEncoding("BIG5"));
        assertSame(Language.JAPANESE, Language.fromEncoding("EUC-jp"));
        assertSame(Language.JAPANESE, Language.fromEncoding("ISO-2022-jp"));
        assertSame(Language.JAPANESE, Language.fromEncoding("Shift-JIS"));
        assertSame(Language.KOREAN, Language.fromEncoding("EUC-kr"));
    }

    private static void assertLanguage(Language expected, String str) {
        assertSame(expected, Language.fromLanguageTag(str));
    }

}
