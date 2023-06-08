// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.language.huggingface;

import com.yahoo.language.tools.EmbedderTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author bjorncs
 */
class HuggingFaceTokenizerTest {

    @TempDir Path tmp;

    @Test
    void bert_tokenizer() throws IOException {
        try (var tokenizer = createTokenizer(tmp, "bert-base-uncased")) {
            var tester = new EmbedderTester(tokenizer);
            tester.assertSegmented("what was the impact of the manhattan project",
                                   "what", "was", "the", "impact", "of", "the", "manhattan", "project");
            tester.assertSegmented("overcommunication", "over", "##com", "##mun", "##ication");
            tester.assertEmbedded("what was the impact of the manhattan project",
                                  "tensor(x[8])",
                                  2054, 2001, 1996, 4254, 1997, 1996, 7128, 2622);
            tester.assertDecoded("what was the impact of the manhattan project");
        }
    }

    @Test
    void tokenizes_using_paraphrase_multilingual_mpnet_base_v2() throws IOException {
        try (var tokenizer = createTokenizer(tmp, "paraphrase-multilingual-mpnet-base-v2")) {
            var tester = new EmbedderTester(tokenizer);
            tester.assertSegmented("h", "▁h");
            tester.assertSegmented("he", "▁he");
            tester.assertSegmented("hel", "▁hel");
            tester.assertSegmented("hello", "▁hell", "o");
            tester.assertSegmented("hei", "▁hei");
            tester.assertSegmented("hei you", "▁hei", "▁you");
            tester.assertSegmented("hei  you", "▁hei", "▁you");
            tester.assertSegmented("this is another sentence", "▁this", "▁is", "▁another", "▁sentence");
            tester.assertSegmented("hello world!", "▁hell", "o", "▁world", "!");
            tester.assertSegmented("Hello, world!", "▁Hello", ",", "▁world", "!");
            tester.assertSegmented("HELLO, world!", "▁H", "ELLO", ",", "▁world", "!");
            tester.assertSegmented("KHJKJHHKJHHSH", "▁KH", "JK", "J", "H", "HK", "J", "HH", "SH");
            tester.assertSegmented("KHJKJHHKJHHSH hello", "▁KH", "JK", "J", "H", "HK", "J", "HH", "SH", "▁hell", "o");
            tester.assertSegmented("  hello  ", "▁hell", "o");
            tester.assertSegmented(")(/&#()/\"\")", "▁", ")(", "/", "&#", "(", ")", "/", "\"", "\")");
            tester.assertSegmented(")(/&#(small)/\"in quotes\")", "▁", ")(", "/", "&#", "(", "s", "mall", ")", "/", "\"", "in", "▁quote", "s", "\")");
            tester.assertSegmented("x.400AS", "▁x", ".", "400", "AS");
            tester.assertSegmented("A normal sentence. Yes one more.", "▁A", "▁normal", "▁sentence", ".", "▁Yes", "▁one", "▁more", ".");

            tester.assertEmbedded("hello, world!", "tensor(d[10])", 33600, 31, 4, 8999, 38);
            tester.assertEmbedded("Hello, world!", "tensor(d[10])", 35378, 4, 8999, 38);
            tester.assertEmbedded("hello, world!", "tensor(d[2])", 33600, 31, 4, 8999, 38);

            tester.assertDecoded("this is a sentence");
            tester.assertDecoded("hello, world!");
            tester.assertDecoded(")(/&#(small)/ \"in quotes\")");
        }
    }

    @Test
    void truncates_to_max_length() throws IOException {
        int maxLength = 3;
        var builder = new HuggingFaceTokenizer.Builder()
                .addDefaultModel(decompressModelFile(tmp, "bert-base-uncased"))
                .setMaxLength(maxLength)
                .setTruncation(true);
        String input = "what was the impact of the manhattan project";
        try (var tokenizerWithoutSpecialTokens = builder.addSpecialTokens(false).build();
             var tokenizerWithSpecialTokens = builder.addSpecialTokens(true).build()) {
            assertMaxLengthRespected(maxLength, tokenizerWithoutSpecialTokens.encode(input));
            assertMaxLengthRespected(maxLength, tokenizerWithSpecialTokens.encode(input));
        }
    }

    private static void assertMaxLengthRespected(int maxLength, Encoding encoding) {
        assertEquals(maxLength, encoding.ids().size());
        assertEquals(maxLength, encoding.tokens().size());
        assertEquals(maxLength, encoding.attentionMask().size());
        assertEquals(maxLength, encoding.typeIds().size());
    }

    private static HuggingFaceTokenizer createTokenizer(Path tmp, String model) throws IOException {
        return new HuggingFaceTokenizer.Builder()
                .addSpecialTokens(false)
                .addDefaultModel(decompressModelFile(tmp, model))
                .build();
    }

    private static Path decompressModelFile(Path tmp, String model) throws IOException {
        var source = Paths.get("src/test/models/huggingface/%s.json.gz".formatted(model));
        Path destination = tmp.resolve(source.getFileName().toString().replace(".gz", ""));
        try (InputStream in = new GZIPInputStream(Files.newInputStream(source));
             OutputStream out = Files.newOutputStream(destination, StandardOpenOption.CREATE)) {
            in.transferTo(out);
        }
        return destination;
    }

}