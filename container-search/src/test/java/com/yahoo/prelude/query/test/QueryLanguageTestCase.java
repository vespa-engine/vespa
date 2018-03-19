// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.language.Language;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.PhraseItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * <p>Tests that the correct query language strings are generated for various
 * query trees.</p>
 *
 * <p>The opposite direction is tested by
 * {@link com.yahoo.prelude.query.parser.test.ParseTestCase}.
 * Note that the query language statements produced by a query tree is a
 * subset of the statements accepted by the parser.</p>
 *
 * @author bratseth
 */
public class QueryLanguageTestCase {

    @Test
    public void testWord() {
        WordItem w = new WordItem("test");

        assertEquals("test", w.toString());
    }

    @Test
    public void testWordWithIndex() {
        WordItem w = new WordItem("test");

        w.setIndexName("test.index");
        assertEquals("test.index:test", w.toString());
    }

    @Test
    public void testPhrase() {
        PhraseItem p = new PhraseItem();

        p.addItem(new WordItem("part"));
        p.addItem(new WordItem("of"));
        p.addItem(new WordItem("phrase"));
        assertEquals("\"part of phrase\"", p.toString());
    }

    @Test
    public void testPhraseWithIndex() {
        PhraseItem p = new PhraseItem();

        p.addItem(new WordItem("part"));
        p.addItem(new WordItem("of"));
        p.addItem(new WordItem("phrase"));
        p.setIndexName("some.index");
        assertEquals("some.index:\"part of phrase\"", p.toString());
    }

    @Test
    public void testNotItem() {
        NotItem n = new NotItem();

        n.addNegativeItem(new WordItem("notthis"));
        n.addNegativeItem(new WordItem("andnotthis"));
        n.addPositiveItem(new WordItem("butthis"));
        assertEquals("+butthis -notthis -andnotthis", n.toString());
    }

    @Test
    public void testLanguagesInQueryParameter() {
        // Right parameter is the parameter given in the query, as language=
        // Left parameter is the language sent to linguistics

        // Ancient
        assertLanguage(Language.CHINESE_SIMPLIFIED,"zh-cn");
        assertLanguage(Language.CHINESE_SIMPLIFIED,"zh-Hans");
        assertLanguage(Language.CHINESE_SIMPLIFIED,"zh-hans");
        assertLanguage(Language.CHINESE_TRADITIONAL,"zh-tw");
        assertLanguage(Language.CHINESE_TRADITIONAL,"zh-Hant");
        assertLanguage(Language.CHINESE_TRADITIONAL,"zh-hant");
        assertLanguage(Language.CHINESE_TRADITIONAL,"zh");
        assertLanguage(Language.ENGLISH, "en");
        assertLanguage(Language.GERMAN, "de");
        assertLanguage(Language.JAPANESE, "ja");
        assertLanguage(Language.fromLanguageTag("jp") ,"jp");
        assertLanguage(Language.KOREAN, "ko");

        // Since 2.0
        assertLanguage(Language.FRENCH, "fr");
        assertLanguage(Language.SPANISH, "es");
        assertLanguage(Language.ITALIAN, "it");
        assertLanguage(Language.PORTUGUESE, "pt");

        //Since 2.2
        assertLanguage(Language.THAI, "th");
    }

    private void assertLanguage(Language expectedLanguage, String languageParameter) {
        Query query = new Query("?query=test&language=" + languageParameter);
        assertEquals(expectedLanguage, query.getModel().getParsingLanguage());

        /*
        This should also work and give something else than und/unknown
        assertEquals("en", new Query("?query=test&language=en_US").getParsingLanguage().languageCode());
        assertEquals("nb_NO", new Query("?query=test&language=nb_NO").getParsingLanguage().languageCode());
        */
    }

}
