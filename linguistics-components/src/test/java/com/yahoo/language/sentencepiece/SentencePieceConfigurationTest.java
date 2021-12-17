// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.language.sentencepiece;

import com.yahoo.config.FileReference;
import com.yahoo.language.Language;
import com.yahoo.language.tools.EmbedderTester;
import org.junit.Test;

/**
 * @author bratseth
 */
public class SentencePieceConfigurationTest {

    @Test
    public void testEnglishTokenization() {
        var b = new SentencePieceConfig.Builder();
        addModel("unknown", "src/test/models/sentencepiece/en.wiki.bpe.vs10000.model", b);
        var tester = new EmbedderTester(new SentencePieceEmbedder(b.build()));
        tester.assertSegmented("this is another sentence", "▁this", "▁is", "▁another", "▁sentence");
        tester.assertSegmented("KHJKJHHKJHHSH hello", "▁", "KHJKJHHKJHHSH", "▁hel", "lo");
    }

    @Test
    public void testNoCollapse() {
        var b = new SentencePieceConfig.Builder();
        addModel("unknown", "src/test/models/sentencepiece/en.wiki.bpe.vs10000.model", b);
        b.collapseUnknowns(false);
        var tester = new EmbedderTester(new SentencePieceEmbedder(b.build()));
        tester.assertSegmented("KHJ hello", "▁", "K", "H", "J", "▁hel", "lo");
    }

    @Test
    public void testHighestScore() {
        var b = new SentencePieceConfig.Builder();
        addModel("unknown", "src/test/models/sentencepiece/en.wiki.bpe.vs10000.model", b);
        b.scoring(SentencePieceConfig.Scoring.highestScore);
        var tester = new EmbedderTester(new SentencePieceEmbedder(b.build()));
        tester.assertSegmented("hello", "▁h", "el", "lo");
    }

    @Test
    public void testMultiLanguageTokenization() {
        var b = new SentencePieceConfig.Builder();
        addModel("ja", "src/test/models/sentencepiece/ja.wiki.bpe.vs5000.model", b);
        addModel("en", "src/test/models/sentencepiece/en.wiki.bpe.vs10000.model", b);
        var tester = new EmbedderTester(new SentencePieceEmbedder(b.build()));
        tester.assertSegmented(Language.JAPANESE, "いくつかの通常のテキスト", "▁", "いく", "つか", "の", "通常", "の", "テ", "キ", "スト");
        tester.assertSegmented(Language.ENGLISH, "hello", "▁hel", "lo");
        tester.assertSegmented(Language.JAPANESE, "hello", "▁h", "ell", "o");
    }

    private void addModel(String language, String file, SentencePieceConfig.Builder b) {
        var mb = new SentencePieceConfig.Model.Builder();
        mb.language(language);
        mb.path(new FileReference(file));
        b.model(mb);
    }

}
