// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.language.sentencepiece;

import com.yahoo.language.Language;
import com.yahoo.tensor.Tensor;
import org.junit.Test;

import java.io.File;

/**
 * @author bratseth
 */
public class SentencePieceTest {

    @Test
    public void testEnglishTokenization() {
        var tester = new SentencePieceTester(new File("src/test/models/sentencepiece/en.wiki.bpe.vs10000.model").toPath());
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
    public void testIntegerListEncoding() {
        var tester = new SentencePieceTester(new File("src/test/models/sentencepiece/en.wiki.bpe.vs10000.model").toPath());
        tester.assertEncoded("hello, world!", 908, 1418, 9934, 501, 9960);
        tester.assertEncoded("Hello, world!", 9912, 0, 6595, 9934, 501, 9960);
    }

    @Test
    public void testDenseTensorEncoding() {
        var tester = new SentencePieceTester(new File("src/test/models/sentencepiece/en.wiki.bpe.vs10000.model").toPath());
        tester.assertEncoded("hello, world!", "tensor(d[10])", "[908,1418,9934,501,9960,0,0,0,0,0]");
        tester.assertEncoded("Hello, world!", "tensor(d[10])", "[9912,0,6595,9934,501,9960,0,0,0,0]");
        tester.assertEncoded("hello, world!", "tensor(d[2])", "[908,1418]");
    }

    @Test
    public void testSparseTensorEncoding() {
        var tester = new SentencePieceTester(new File("src/test/models/sentencepiece/en.wiki.bpe.vs10000.model").toPath());
        tester.assertEncoded("hello", "tensor(token{})", "{lo:1.0,'▁hel':0.0}");
    }

    @Test
    public void testNoCollapse() {
        var tester = new SentencePieceTester(new SentencePieceEncoder.Builder()
                                                     .addDefaultModel(new File("src/test/models/sentencepiece/en.wiki.bpe.vs10000.model").toPath())
                                                     .setCollapseUnknowns(false));
        tester.assertSegmented("KHJ hello", "▁", "K", "H", "J", "▁hel", "lo");
    }

    @Test
    public void testHighestScore() {
        var tester = new SentencePieceTester(new SentencePieceEncoder.Builder()
                                                     .addDefaultModel(new File("src/test/models/sentencepiece/en.wiki.bpe.vs10000.model").toPath())
                                                     .setScoring(SentencePieceEncoder.Scoring.highestScore));
        tester.assertSegmented("h", "▁h");
        tester.assertSegmented("he", "▁he");
        tester.assertSegmented("hel", "▁h", "el");
        tester.assertSegmented("hello", "▁h", "el", "lo");
    }

    @Test
    public void testMultiLanguageTokenization() {
        SentencePieceEncoder.Builder builder = new SentencePieceEncoder.Builder();
        builder.addModel(Language.JAPANESE, new File("src/test/models/sentencepiece/ja.wiki.bpe.vs5000.model").toPath());
        builder.addModel(Language.ENGLISH, new File("src/test/models/sentencepiece/en.wiki.bpe.vs10000.model").toPath());
        var tester = new SentencePieceTester(builder);
        tester.assertSegmented(Language.JAPANESE, "いくつかの通常のテキスト", "▁", "いく", "つか", "の", "通常", "の", "テ", "キ", "スト");
        tester.assertSegmented(Language.ENGLISH, "hello", "▁hel", "lo");
        tester.assertSegmented(Language.JAPANESE, "hello", "▁h", "ell", "o");
    }

}
