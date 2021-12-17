// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.language.sentencepiece;

import com.yahoo.language.Language;
import com.yahoo.language.tools.EmbedderTester;
import org.junit.Test;

import java.io.File;

/**
 * @author bratseth
 */
public class SentencePieceTest {

    @Test
    public void testEnglishSegmenting() {
        var tester = new EmbedderTester(new SentencePieceEmbedder.Builder("src/test/models/sentencepiece/en.wiki.bpe.vs10000.model").build());
        tester.assertSegmented("h", "▁h");
        tester.assertSegmented("he", "▁he");
        tester.assertSegmented("hel", "▁hel");
        tester.assertSegmented("hello", "▁hel", "lo");
        tester.assertSegmented("hei", "▁he", "i");
        tester.assertSegmented("hei you", "▁he", "i", "▁you");
        tester.assertSegmented("hei  you", "▁he", "i", "▁you");
        tester.assertSegmented("this is another sentence", "▁this", "▁is", "▁another", "▁sentence");
        tester.assertSegmented("hello world!", "▁hel", "lo", "▁world", "!");
        tester.assertSegmented("Hello, world!", "▁", "H", "ello", ",", "▁world", "!");
        tester.assertSegmented("HELLO, world!", "▁", "HELLO", ",", "▁world", "!");
        tester.assertSegmented("KHJKJHHKJHHSH", "▁", "KHJKJHHKJHHSH");
        tester.assertSegmented("KHJKJHHKJHHSH hello", "▁", "KHJKJHHKJHHSH", "▁hel", "lo");
        tester.assertSegmented("  hello  ", "▁hel", "lo");
        tester.assertSegmented(")(/&#()/\"\")", "▁)", "(", "/", "&", "#", "(", ")", "/", "\"", "\")");
        tester.assertSegmented(")(/&#(small)/\"in quotes\")", "▁)", "(", "/", "&", "#", "(", "sm", "all", ")", "/", "\"", "in", "▁qu", "otes", "\")");
        tester.assertSegmented("x.400AS", "▁x", ".", "4", "00", "AS");
        tester.assertSegmented("A normal sentence. Yes one more.", "▁", "A", "▁normal", "▁sentence", ".", "▁", "Y", "es", "▁one", "▁more", ".");
    }

    @Test
    public void testEnglishEmbedding() {
        var tester = new EmbedderTester(new SentencePieceEmbedder.Builder("src/test/models/sentencepiece/en.wiki.bpe.vs10000.model").build());
        tester.assertEmbedded("hello, world!", "tensor(d[10])", 908, 1418, 9934, 501, 9960);
        tester.assertEmbedded("Hello, world!", "tensor(d[10])", 9912, 0, 6595, 9934, 501, 9960);
        tester.assertEmbedded("hello, world!", "tensor(d[2])", 908, 1418, 9934, 501, 9960);
    }

    @Test
    public void testNoCollapse() {
        var builder = new SentencePieceEmbedder.Builder()
                .addDefaultModel(new File("src/test/models/sentencepiece/en.wiki.bpe.vs10000.model").toPath())
                .setCollapseUnknowns(false);
        var tester = new EmbedderTester(builder.build());
        tester.assertSegmented("KHJ hello", "▁", "K", "H", "J", "▁hel", "lo");
    }

    @Test
    public void testHighestScore() {
        var builder = new SentencePieceEmbedder.Builder()
                .addDefaultModel(new File("src/test/models/sentencepiece/en.wiki.bpe.vs10000.model").toPath())
                .setScoring(Scoring.highestScore);
        var tester = new EmbedderTester(builder.build());
        tester.assertSegmented("h", "▁h");
        tester.assertSegmented("he", "▁he");
        tester.assertSegmented("hel", "▁h", "el");
        tester.assertSegmented("hello", "▁h", "el", "lo");
    }

    @Test
    public void testMultiLanguageTokenization() {
        SentencePieceEmbedder.Builder builder = new SentencePieceEmbedder.Builder();
        builder.addModel(Language.JAPANESE, new File("src/test/models/sentencepiece/ja.wiki.bpe.vs5000.model").toPath());
        builder.addModel(Language.ENGLISH, new File("src/test/models/sentencepiece/en.wiki.bpe.vs10000.model").toPath());
        var tester = new EmbedderTester(builder.build());
        tester.assertSegmented(Language.JAPANESE, "いくつかの通常のテキスト", "▁", "いく", "つか", "の", "通常", "の", "テ", "キ", "スト");
        tester.assertSegmented(Language.ENGLISH, "hello", "▁hel", "lo");
        tester.assertSegmented(Language.JAPANESE, "hello", "▁h", "ell", "o");
    }

}
