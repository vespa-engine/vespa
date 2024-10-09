// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespasignificance;

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
import io.airlift.compress.zstd.ZstdInputStream;
import io.airlift.compress.zstd.ZstdOutputStream;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @author MariusArhaug
 */
public class SignificanceModelGenerator {

    private final ClientParameters clientParameters;
    private final Tokenizer tokenizer;
    private final TreeMap<String, Long> documentFrequency = new TreeMap<>();

    private final List<Language> languages;

    private final Language languageTag;
    private final ObjectMapper objectMapper;
    private final static JsonFactory parserFactory = new JsonFactory();

    final DocumentTypeManager types = new DocumentTypeManager();
    final DocumentType docType;
    private final boolean useZstCompression;
    private final static String VERSION = "1.0";
    private final static String ID = "1";
    private final static String SIGNIFICANCE_DESCRIPTION = "Significance model for input file";
    private final static String DOC_FREQ_DESCRIPTION = "Document frequency for language";

    private final static String DUMMY_DOC_TYPE = "dummy";
    private final static String DUMMY_DOC_ID = "id:dummy:" + DUMMY_DOC_TYPE + "::dummy";

    public SignificanceModelGenerator(ClientParameters clientParameters) {
        this.clientParameters = clientParameters;

        if (clientParameters.zstCompression && !clientParameters.outputFile.endsWith(".zst")) {
            throw new IllegalArgumentException("Output file must have .zst extension when using zst compression");
        }

        if (!clientParameters.zstCompression && clientParameters.outputFile.endsWith(".zst")) {
            throw new IllegalArgumentException("Output file must not have .zst extension when not using zst compression");
        }

        this.languages = Arrays.stream(clientParameters.language.split(","))
                .map(Language::fromLanguageTag)
                .collect(Collectors.toList());

        this.languageTag = this.languages.get(0);

        OpenNlpLinguistics openNlpLinguistics = new OpenNlpLinguistics();
        tokenizer = openNlpLinguistics.getTokenizer();
        objectMapper = new ObjectMapper();

        docType = new DocumentType(DUMMY_DOC_TYPE);
        docType.addField(new Field(clientParameters.field, DataType.STRING));
        useZstCompression = clientParameters.zstCompression;

        types.registerDocumentType(docType);
    }


    public void generate() throws IOException {

        Path currentWorkingDir = Paths.get("").toAbsolutePath();

        final InputStream rawDoc = Files.newInputStream(currentWorkingDir.resolve(clientParameters.inputFile));

        BufferedReader reader = new BufferedReader(new InputStreamReader(rawDoc));

        long i = 1;
        while (reader.ready()) {
            String line = reader.readLine();

            // Avoid failing on empty lines
            if (line.isBlank())
                continue;

            JsonReader jsonReader = new JsonReader(types, new ByteArrayInputStream(Utf8.toBytes(line)), parserFactory);

            // Using DUMMY_DOC_ID since we are only interested in content.
            ParsedDocumentOperation operation = jsonReader.readSingleDocumentStreaming(DocumentOperationType.PUT, DUMMY_DOC_ID);

            DocumentPut put = (DocumentPut) operation.operation();
            Document document = put.getDocument();
            FieldValue fieldValue = document.getFieldValue(clientParameters.field);
            this.handleTokenization(fieldValue.toString());
            if (i % 50000 == 0) {
                System.out.println("Documents processed: " + i + ", unique words: " + documentFrequency.size());
            }
            i++;
        }

        long pageCount = i - 1;
        System.out.println("Total documents processed: " + pageCount + ", unique words: " + documentFrequency.size());

        SignificanceModelFile modelFile;
        File outputFile = Paths.get(clientParameters.outputFile).toFile();
        String languagesKey = String.join(",", this.languages.stream().map(Language::languageCode).toList());
        if (outputFile.exists()) {

            InputStream in = outputFile.toString().endsWith(".zst") ?
                    new ZstdInputStream(new FileInputStream(outputFile)) :
                    new FileInputStream(outputFile);

            modelFile = objectMapper.readValue(in, SignificanceModelFile.class);

            modelFile.addLanguage(languagesKey, new DocumentFrequencyFile(DOC_FREQ_DESCRIPTION, pageCount, getFinalDocumentFrequency()));

        } else {
            HashMap<String, DocumentFrequencyFile> languages = new HashMap<>() {{
                put(languagesKey, new DocumentFrequencyFile(DOC_FREQ_DESCRIPTION, pageCount, getFinalDocumentFrequency()));
            }};

            modelFile = new SignificanceModelFile(VERSION, ID, SIGNIFICANCE_DESCRIPTION + clientParameters.inputFile, languages);
        }
        try {
            ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();

            OutputStream out = useZstCompression ?
                    new ZstdOutputStream(new FileOutputStream(clientParameters.outputFile)) :
                    new FileOutputStream(clientParameters.outputFile);

            writer.writeValue(out, modelFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write model to output file", e);
        }
    }

    private void handleTokenization(String field) {
        var tokens = tokenizer.tokenize(field, languageTag, StemMode.NONE, false);

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
