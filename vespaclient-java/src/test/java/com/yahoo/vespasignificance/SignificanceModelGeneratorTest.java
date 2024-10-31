// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespasignificance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.language.significance.impl.DocumentFrequencyFile;
import com.yahoo.language.significance.impl.SignificanceModelFile;
import io.airlift.compress.zstd.ZstdInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author MariusArhaug
 */
public class SignificanceModelGeneratorTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    private Path tempDir;

    private ClientParameters.Builder createParameters(String inputPath, String outputPath, String field, String language, String zstCompression) {
        tempDir.toFile().mkdirs();

        return new ClientParameters.Builder()
                .setInputFile("src/test/files/" + inputPath)
                .setOutputFile(tempDir.resolve(outputPath).toString())
                .setField(field)
                .setLanguage(language)
                .setZstCompression(zstCompression);
    }

    private SignificanceModelGenerator createSignificanceModelGenerator(ClientParameters params) {
        return new SignificanceModelGenerator(params);
    }

    private void assertNorwegianFrequencies1(DocumentFrequencyFile documentFrequencyFile) {
        assertEquals(3, documentFrequencyFile.documentCount());
        assertEquals(3, documentFrequencyFile.frequencies().get("norske"));
        assertEquals(2, documentFrequencyFile.frequencies().get("wiki"));
        assertFalse(documentFrequencyFile.frequencies().containsKey("sider"));
    }


    private void assertNorwegianFrequencies2(DocumentFrequencyFile documentFrequencyFile) {
        assertEquals(3, documentFrequencyFile.documentCount());
        assertEquals(3, documentFrequencyFile.frequencies().get("norske"));
        assertEquals(2, documentFrequencyFile.frequencies().get("wikipedia"));
        assertFalse(documentFrequencyFile.frequencies().containsKey("sider"));
    }

    private void assertEnglishFrequencies(DocumentFrequencyFile documentFrequencyFile) {
        assertEquals(3, documentFrequencyFile.documentCount());
        assertEquals(3, documentFrequencyFile.frequencies().get("english"));
        assertEquals(2, documentFrequencyFile.frequencies().get("wiki"));
        assertFalse(documentFrequencyFile.frequencies().containsKey("pages"));
    }

    @Test
    void testGenerateSimpleFile() throws IOException {
        String inputPath = "no_1.jsonl";
        String outputPath = "output.json";
        ClientParameters params = createParameters(inputPath, outputPath,  "text", "nb", "false").build();
        SignificanceModelGenerator generator = createSignificanceModelGenerator(params);
        generator.generate();

        File outputFile = new File(tempDir.resolve(outputPath).toString());
        assertTrue(outputFile.exists());

        SignificanceModelFile modelFile = objectMapper.readValue(outputFile, SignificanceModelFile.class);
        HashMap<String, DocumentFrequencyFile> languages = modelFile.languages();
        assertEquals(1, languages.size());
        assertTrue(languages.containsKey("nb"));

        DocumentFrequencyFile documentFrequencyFile = languages.get("nb");
        assertNorwegianFrequencies1(documentFrequencyFile);
    }

    @Test
    void testGenerateSimpleFileWithZST() throws IOException {
        String inputPath = "no_1.jsonl";
        ClientParameters params1 = createParameters(inputPath, "output.json",  "text", "nb", "true").build();

        // Throws exception when outputfile does not have .zst extension when using zst compression
        assertThrows(IllegalArgumentException.class, () -> createSignificanceModelGenerator(params1));

        String outputPath = "output.json.zst";
        ClientParameters params = createParameters(inputPath, outputPath,  "text", "nb", "true").build();

        SignificanceModelGenerator generator = createSignificanceModelGenerator(params);
        generator.generate();

        File outputFile = new File(tempDir.resolve(outputPath ).toString());
        assertTrue(outputFile.exists());

        InputStream in = new ZstdInputStream(new FileInputStream(outputFile));
        SignificanceModelFile modelFile = objectMapper.readValue(in, SignificanceModelFile.class);
        HashMap<String, DocumentFrequencyFile> languages = modelFile.languages();
        assertEquals(1, languages.size());
        assertTrue(languages.containsKey("nb"));

        DocumentFrequencyFile documentFrequencyFile = languages.get("nb");
        assertNorwegianFrequencies1(documentFrequencyFile);
    }

    @Test
    void testGenerateFileWithMultipleLanguages() throws IOException {
        String inputPath = "no_1.jsonl";
        String outputPath = "output.json";
        ClientParameters params1 = createParameters(inputPath, outputPath, "text", "nb",  "false").build();
        SignificanceModelGenerator generator = createSignificanceModelGenerator(params1);
        generator.generate();

        File outputFile = new File(tempDir.resolve(outputPath).toString());
        assertTrue(outputFile.exists());

        String inputPath2 = "en.jsonl";
        ClientParameters params2 = createParameters(inputPath2,  outputPath, "text", "en",  "false").build();
        generator = createSignificanceModelGenerator(params2);
        generator.generate();

        outputFile = new File(tempDir.resolve(outputPath).toString());
        assertTrue(outputFile.exists());

        SignificanceModelFile modelFile = objectMapper.readValue(outputFile, SignificanceModelFile.class);
        HashMap<String, DocumentFrequencyFile> languages = modelFile.languages();
        assertEquals(2, languages.size());
        assertTrue(languages.containsKey("nb"));
        assertTrue(languages.containsKey("en"));

        DocumentFrequencyFile nb = languages.get("nb");
        DocumentFrequencyFile en = languages.get("en");

        assertNorwegianFrequencies1(nb);
        assertEnglishFrequencies(en);
    }

    @Test
    void testOverwriteExistingDocumentFrequencyLanguage() throws IOException {
        String inputPath = "no_1.jsonl";
        String outputPath = "output.json";
        ClientParameters params1 = createParameters(inputPath, outputPath,  "text", "nb", "false").build();
        SignificanceModelGenerator generator = createSignificanceModelGenerator(params1);
        generator.generate();

        File outputFile = new File(tempDir.resolve(outputPath).toString());
        assertTrue(outputFile.exists());

        SignificanceModelFile preUpdatedFile = objectMapper.readValue(outputFile, SignificanceModelFile.class);
        HashMap<String, DocumentFrequencyFile> oldLanguages = preUpdatedFile.languages();
        assertEquals(1, oldLanguages.size());
        assertTrue(oldLanguages.containsKey("nb"));

        DocumentFrequencyFile oldDf = oldLanguages.get("nb");
        assertNorwegianFrequencies1(oldDf);
        assertFalse(oldDf.frequencies().containsKey("wikipedia"));

        String inputPath2 = "no_2.jsonl";
        ClientParameters params2 = createParameters(inputPath2, outputPath, "text", "nb", "false").build();
        SignificanceModelGenerator generator2 = createSignificanceModelGenerator(params2);
        generator2.generate();

        outputFile = new File(tempDir.resolve(outputPath).toString());
        assertTrue(outputFile.exists());

        SignificanceModelFile modelFile = objectMapper.readValue(outputFile, SignificanceModelFile.class);
        HashMap<String, DocumentFrequencyFile> languages = modelFile.languages();
        assertEquals(1, languages.size());
        assertTrue(languages.containsKey("nb"));

        DocumentFrequencyFile df = languages.get("nb");
        assertNorwegianFrequencies2(df);
    }

    @Test
    void testGenerateFileWithMultipleLanguagesForSingleDocumentFrequency() throws IOException {
        String inputPath = "no_1.jsonl";
        String outputPath = "output.json";
        ClientParameters params = createParameters(inputPath, outputPath,  "text", "nb,un",  "false").build();
        SignificanceModelGenerator generator = createSignificanceModelGenerator(params);
        generator.generate();

        File outputFile = new File(tempDir.resolve(outputPath).toString());
        assertTrue(outputFile.exists());

        SignificanceModelFile modelFile = objectMapper.readValue(outputFile, SignificanceModelFile.class);
        HashMap<String, DocumentFrequencyFile> languages = modelFile.languages();
        assertEquals(1, languages.size());
        assertTrue(languages.containsKey("nb,un"));

        DocumentFrequencyFile documentFrequencyFile = languages.get("nb,un");
        assertNorwegianFrequencies1(documentFrequencyFile);
    }
}
