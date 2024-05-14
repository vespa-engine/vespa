// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespasignificance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.language.significance.impl.DocumentFrequencyFile;
import com.yahoo.language.significance.impl.SignificanceModelFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author MariusArhaug
 */
public class SignificanceModelGeneratorTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void removeTempDirectory() {
        Path tempDir = Paths.get("src/test/files/temp-output");
        if (tempDir.toFile().exists()) {
            tempDir.toFile().delete();
        }
    }

    private static ClientParameters.Builder createParameters(String inputFile, String field, String language) {
        String outputPath = "src/test/files/temp-dir";
        Paths.get(outputPath).toFile().mkdirs();

        return new ClientParameters.Builder()
                .setInputFile("src/test/files/" + inputFile)
                .setOutputFile(outputPath + "/output.json")
                .setField(field)
                .setLanguage(language);
    }

    private SignificanceModelGenerator createSignificanceModelGenerator(ClientParameters params) {
        return new SignificanceModelGenerator(params);
    }


    @Test
    void testGenerateSimpleFile() throws IOException {
        ClientParameters params = createParameters("no.jsonl", "text", "NB").build();
        SignificanceModelGenerator generator = createSignificanceModelGenerator(params);
        generator.generate();

        assertTrue(Paths.get("src/test/files/temp-dir/output.json").toFile().exists());

        SignificanceModelFile modelFile = objectMapper.readValue(Paths.get("src/test/files/temp-dir/output.json").toFile(), SignificanceModelFile.class);

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
        ClientParameters params1 = createParameters("no.jsonl", "text", "NB").build();
        SignificanceModelGenerator generator = createSignificanceModelGenerator(params1);
        generator.generate();
        assertTrue(Paths.get("src/test/files/temp-dir/output.json").toFile().exists());

        ClientParameters params2 = createParameters("en.jsonl", "text", "EN").build();
        generator = createSignificanceModelGenerator(params2);
        generator.generate();

        assertTrue(Paths.get("src/test/files/temp-dir/output.json").toFile().exists());

        SignificanceModelFile modelFile = objectMapper.readValue(Paths.get("src/test/files/temp-dir/output.json").toFile(), SignificanceModelFile.class);

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
        ClientParameters params1 = createParameters("no.jsonl", "text", "NB").build();
        SignificanceModelGenerator generator = createSignificanceModelGenerator(params1);
        generator.generate();
        assertTrue(Paths.get("src/test/files/temp-dir/output.json").toFile().exists());

        SignificanceModelFile preUpdatedFile = objectMapper.readValue(Paths.get("src/test/files/temp-dir/output.json").toFile(), SignificanceModelFile.class)
                ;
        HashMap<String, DocumentFrequencyFile> oldLanguages = preUpdatedFile.languages();
        assertEquals(1, oldLanguages.size());

        assertTrue(oldLanguages.containsKey("NB"));

        DocumentFrequencyFile oldDf = oldLanguages.get("NB");

        assertEquals(3, oldDf.frequencies().get("fra"));
        assertEquals(3, oldDf.frequencies().get("skriveform"));
        assertFalse(oldDf.frequencies().containsKey("nytt"));

        ClientParameters params2 = createParameters("no_2.jsonl", "text", "NB").build();
        SignificanceModelGenerator generator2 = createSignificanceModelGenerator(params2);
        generator2.generate();

        assertTrue(Paths.get("src/test/files/temp-dir/output.json").toFile().exists());

        SignificanceModelFile modelFile = objectMapper.readValue(Paths.get("src/test/files/temp-dir/output.json").toFile(), SignificanceModelFile.class);

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
