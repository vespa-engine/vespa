// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespasignificance;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.json.DocumentOperationType;
import com.yahoo.document.json.JsonReader;
import com.yahoo.document.json.ParsedDocumentOperation;
import com.yahoo.language.Language;
import com.yahoo.language.opennlp.OpenNlpLinguistics;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenScript;
import com.yahoo.language.process.TokenType;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.significance.impl.DocumentFrequencyFile;
import com.yahoo.language.significance.impl.SignificanceModelFile;
import com.yahoo.text.Utf8;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @author MariusArhaug
 */
public class SignificanceModelGenerator {

    private final ClientParameters clientParameters;
    private final Tokenizer tokenizer;
    private final TreeMap<String, Long> documentFrequency = new TreeMap<>();

    private final Language language;
    private final ObjectMapper objectMapper;
    private final static JsonFactory parserFactory = new JsonFactory();

    final DocumentTypeManager types = new DocumentTypeManager();
    final DocumentType docType;
    private final static String VERSION = "1.0";
    private final static String ID = "1";
    private final static String SIGNIFICANCE_DESCRIPTION = "Significance model for input file";
    private final static String DOC_FREQ_DESCRIPTION = "Document frequency for language";

    public SignificanceModelGenerator(ClientParameters clientParameters) {
        this.clientParameters = clientParameters;
        OpenNlpLinguistics openNlpLinguistics = new OpenNlpLinguistics();
        tokenizer = openNlpLinguistics.getTokenizer();
        objectMapper = new ObjectMapper();

        language = Language.fromLanguageTag(clientParameters.language);

        docType = new DocumentType(clientParameters.docType);
        docType.addField(new Field(clientParameters.field, DataType.STRING));
        types.registerDocumentType(docType);
    }


    public void generate() throws IOException {

        Path currentWorkingDir = Paths.get("").toAbsolutePath();

        final InputStream rawDoc = Files.newInputStream(currentWorkingDir.resolve(clientParameters.inputFile));

        BufferedReader reader = new BufferedReader(new InputStreamReader(rawDoc));

        long i = 1;
        while (reader.ready()) {
            String line = reader.readLine();
            JsonReader jsonReader = new JsonReader(types, new ByteArrayInputStream(Utf8.toBytes(line)), parserFactory);
            String wikimediaId = "id:wikimedia:" + language.languageCode() + "::" + i;

            ParsedDocumentOperation operation = jsonReader.readSingleDocumentStreaming(DocumentOperationType.PUT, wikimediaId);
            DocumentPut put = (DocumentPut) operation.operation();
            Document document = put.getDocument();
            FieldValue fieldValue = document.getFieldValue(clientParameters.field);
            this.handleTokenization(fieldValue.toString());
            i++;
        }

        long pageCount = i - 1;

        SignificanceModelFile modelFile;
        if (Paths.get(clientParameters.outputFile).toFile().exists()) {
            modelFile = objectMapper.readValue(new File(clientParameters.outputFile), SignificanceModelFile.class);

            modelFile.addLanguage(clientParameters.language, new DocumentFrequencyFile(DOC_FREQ_DESCRIPTION, pageCount, getFinalDocumentFrequency()));

        } else {
            HashMap<String, DocumentFrequencyFile> languages = new HashMap<>() {{
                put(clientParameters.language, new DocumentFrequencyFile(DOC_FREQ_DESCRIPTION, pageCount, getFinalDocumentFrequency()));
            }};

            modelFile = new SignificanceModelFile(VERSION, ID, SIGNIFICANCE_DESCRIPTION + clientParameters.inputFile, languages);
        }
        try {
            ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();
            writer.writeValue(new File(clientParameters.outputFile), modelFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write model to output file", e);
        }
    }

    private void handleTokenization(String field) {
        var tokens = tokenizer.tokenize(field, language, StemMode.ALL, false);

        Set<String> uniqueWords = StreamSupport.stream(tokens.spliterator(), false)
                .filter(t -> t.getType() == TokenType.ALPHABETIC)
                .filter(t -> t.getScript() == TokenScript.LATIN)
                .map(Token::getTokenString)
                .collect(Collectors.toSet());

        for (String word : uniqueWords) {
            if (documentFrequency.containsKey(word)) {
                documentFrequency.merge(word, 1L, Long::sum);
            } else {
                documentFrequency.put(word, 1L);
            }
        }
    }

    public Map<String, Long> getFinalDocumentFrequency() {
        return documentFrequency.entrySet().stream()
                .filter(k -> k.getValue() > 1)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        TreeMap::new
                ));
    }
}
