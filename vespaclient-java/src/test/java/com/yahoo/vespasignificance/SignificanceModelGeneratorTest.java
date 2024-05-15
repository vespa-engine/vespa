// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespasignificance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.language.significance.impl.DocumentFrequencyFile;
import com.yahoo.language.significance.impl.SignificanceModelFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
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

    private ClientParameters.Builder createParameters(String inputPath, String outputPath, String field, String language, String docType) {
        tempDir.toFile().mkdirs();

        return new ClientParameters.Builder()
                .setInputFile("src/test/files/" + inputPath)
                .setOutputFile(tempDir.resolve(outputPath).toString())
                .setField(field)
                .setLanguage(language)
                .setDocType(docType);
    }

    private SignificanceModelGenerator createSignificanceModelGenerator(ClientParameters params) {
        return new SignificanceModelGenerator(params);
    }

    @Test
    void testGenerateSimpleFile() throws IOException {
        String inputPath = "no.jsonl";
        String outputPath = "output.json";
        ClientParameters params = createParameters(inputPath, outputPath,  "text", "NB", "nb").build();
        SignificanceModelGenerator generator = createSignificanceModelGenerator(params);
        generator.generate();

        File outputFile = new File(tempDir.resolve(outputPath).toString());
        assertTrue(outputFile.exists());

        SignificanceModelFile modelFile = objectMapper.readValue(outputFile, SignificanceModelFile.class);

        HashMap<String, DocumentFrequencyFile> languages = modelFile.languages();
        assertEquals(1, languages.size());

        assertTrue(languages.containsKey("NB"));

        DocumentFrequencyFile documentFrequencyFile = languages.get("NB");

        assertEquals(3, documentFrequencyFile.frequencies().get("fra"));
        assertEquals(3, documentFrequencyFile.frequencies().get("skriveform"));
        assertEquals(3, documentFrequencyFile.frequencies().get("kategori"));
        assertEquals(3, documentFrequencyFile.frequencies().get("eldr"));

    }

    @Test
    void testGenerateFileWithMultipleLanguages() throws IOException {
        String inputPath = "no.jsonl";
        String outputPath = "output.json";
        ClientParameters params1 = createParameters(inputPath, outputPath, "text", "NB", "nb").build();
        SignificanceModelGenerator generator = createSignificanceModelGenerator(params1);
        generator.generate();

        File outputFile = new File(tempDir.resolve(outputPath).toString());
        assertTrue(outputFile.exists());

        String inputPath2 = "en.jsonl";
        ClientParameters params2 = createParameters(inputPath2,  outputPath, "text", "EN", "en").build();
        generator = createSignificanceModelGenerator(params2);
        generator.generate();

        outputFile = new File(tempDir.resolve(outputPath).toString());
        assertTrue(outputFile.exists());

        SignificanceModelFile modelFile = objectMapper.readValue(outputFile, SignificanceModelFile.class);

        HashMap<String, DocumentFrequencyFile> languages = modelFile.languages();

        assertEquals(2, languages.size());

        assertTrue(languages.containsKey("NB"));
        assertTrue(languages.containsKey("EN"));

        DocumentFrequencyFile nb = languages.get("NB");
        DocumentFrequencyFile en = languages.get("EN");

        assertEquals(3, nb.documentCount());
        assertEquals(3, en.documentCount());

        assertEquals(3, nb.frequencies().get("fra"));
        assertEquals(3, nb.frequencies().get("skriveform"));

        assertEquals(3, en.frequencies().get("some"));
        assertEquals(3, en.frequencies().get("wiki"));

    }

    @Test
    void testOverwriteExistingDocumentFrequencyLanguage() throws IOException {
        String inputPath = "no.jsonl";
        String outputPath = "output.json";
        ClientParameters params1 = createParameters(inputPath, outputPath,  "text", "NB", "nb").build();
        SignificanceModelGenerator generator = createSignificanceModelGenerator(params1);
        generator.generate();

        File outputFile = new File(tempDir.resolve(outputPath).toString());
        assertTrue(outputFile.exists());

        SignificanceModelFile preUpdatedFile = objectMapper.readValue(outputFile, SignificanceModelFile.class)
                ;
        HashMap<String, DocumentFrequencyFile> oldLanguages = preUpdatedFile.languages();
        assertEquals(1, oldLanguages.size());

        assertTrue(oldLanguages.containsKey("NB"));

        DocumentFrequencyFile oldDf = oldLanguages.get("NB");

        assertEquals(3, oldDf.frequencies().get("fra"));
        assertEquals(3, oldDf.frequencies().get("skriveform"));
        assertFalse(oldDf.frequencies().containsKey("nytt"));

        String inputPath2 = "no_2.jsonl";
        ClientParameters params2 = createParameters(inputPath2, outputPath, "text", "NB", "nb").build();
        SignificanceModelGenerator generator2 = createSignificanceModelGenerator(params2);
        generator2.generate();

        outputFile = new File(tempDir.resolve(outputPath).toString());
        assertTrue(outputFile.exists());

        SignificanceModelFile modelFile = objectMapper.readValue(outputFile, SignificanceModelFile.class);

        HashMap<String, DocumentFrequencyFile> languages = modelFile.languages();

        assertEquals(1, languages.size());

        assertTrue(languages.containsKey("NB"));

        DocumentFrequencyFile df = languages.get("NB");

        assertEquals(2, df.frequencies().get("fra"));
        assertEquals(3, df.frequencies().get("skriveform"));
        assertTrue(df.frequencies().containsKey("nytt"));
        assertEquals(2, df.frequencies().get("nytt"));
    }
}
