// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespasignificance;

import ai.vespa.vespasignificance.generate.FormatStrategy;
import ai.vespa.vespasignificance.generate.JsonlDocumentFormatStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.yahoo.language.Language;
import com.yahoo.language.opennlp.OpenNlpLinguistics;
import com.yahoo.language.significance.impl.DocumentFrequencyFile;
import com.yahoo.language.significance.impl.SignificanceModelFile;
import io.airlift.compress.zstd.ZstdInputStream;
import io.airlift.compress.zstd.ZstdOutputStream;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author MariusArhaug
 * @author johsol
 */
public class SignificanceModelGenerator {

    private final ClientParameters clientParameters;

    private FormatStrategy formatStrategy;
    private ObjectMapper objectMapper;
    private InputFormat format;
    private String outputFile;
    private boolean useZstCompression;

    private final static String VERSION = "1.0";
    private final static String ID = "1";
    private final static String SIGNIFICANCE_DESCRIPTION = "Significance model for input file";
    private final static String DOC_FREQ_DESCRIPTION = "Document frequency for language";


    public SignificanceModelGenerator(ClientParameters clientParameters) {
        this.clientParameters = clientParameters;
    }

    /**
     * Main entry point for generate subcommand. Returns 0 on success.
     */
    public int run() {
        try {
            setFormat();
            if (format == InputFormat.jsonl) {
                validateJsonlFormatRequiredFields();
            }
            resolveAndValidateOutputFile();

            String language = Objects.requireNonNullElse(clientParameters.language, "un");
            List<Language> languageKeyParts = Arrays.stream(language.split(","))
                    .map(Language::fromLanguageTag)
                    .collect(Collectors.toList());

            Language tokenizationLanguage = languageKeyParts.get(0);

            var openNlpLinguistics = new OpenNlpLinguistics();
            var tokenizer = openNlpLinguistics.getTokenizer();
            objectMapper = new ObjectMapper();
            useZstCompression = clientParameters.zstCompression;

            final Path input = Paths.get(clientParameters.inputFile);

            switch (format) {
                case jsonl -> formatStrategy = new JsonlDocumentFormatStrategy(
                        input, tokenizer, tokenizationLanguage, languageKeyParts, clientParameters.field
                );
            }

            generate();
        } catch (GenerateFailure f) {
           return 1;
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            return 1;
        }

        return 0;
    }

    private enum InputFormat {

        /** Vespa documents in JSONL format. */
        jsonl;

        /** Creates a comma separated list of the targets as a String */
        private static String allowed() {
            var byName = Arrays.stream(values()).collect(Collectors.toMap(
                    f -> f.name().toLowerCase(Locale.ROOT),
                    f -> f
            ));
            return String.join(", ", byName.keySet());
        }

    }

    /**
     * Parse format from params. Default is jsonl.
     */
    private void setFormat() {
        try {
            String fmt = Objects.requireNonNullElse(clientParameters.format, InputFormat.jsonl.toString());
            this.format = InputFormat.valueOf(fmt.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            System.err.println("Error: invalid format specified: " + clientParameters.format);
            CommandLineOptions.printGenerateHelp();
            System.err.println("Use --format FORMAT to specify format.");
            System.err.println("Allowed formats: " + InputFormat.allowed());
            throw new GenerateFailure();
        }
    }

    /**
     * Ensure required options for jsonl format.
     */
    private void validateJsonlFormatRequiredFields() {
        List<String> fieldsMissing = new ArrayList<>();

        if (clientParameters.language == null) {
            fieldsMissing.add(CommandLineOptions.LANGUAGE_OPTION);
        }

        if (clientParameters.inputFile == null) {
            fieldsMissing.add(CommandLineOptions.INPUT_OPTION);
        }

        if (clientParameters.outputFile == null) {
            fieldsMissing.add(CommandLineOptions.OUTPUT_OPTION);
        }

        if (clientParameters.field == null) {
            fieldsMissing.add(CommandLineOptions.FIELD_OPTION);
        }

        if (!fieldsMissing.isEmpty()) {
            System.err.println("Missing required options: " + String.join(", ", fieldsMissing));
            CommandLineOptions.printGenerateHelp();
            throw new GenerateFailure();
        }
    }

    /**
     * If user did not provide output file, set it to model.json (or .zst). Legacy behavior: the user must
     * provide output, but this is only kept for jsonl target (see
     * {@link SignificanceModelGenerator#validateJsonlFormatRequiredFields()}).
     */
    private void resolveAndValidateOutputFile() {
        String outputFile;
        if (clientParameters.outputFile == null) {
            // user did not provide output file.
            outputFile = "model.json";
            if (clientParameters.zstCompression) {
                outputFile = outputFile + ".zst";
            }
        } else {
            outputFile = clientParameters.outputFile;
        }

        // Legacy behavior for jsonl
        if (clientParameters.zstCompression && !outputFile.endsWith(".zst")) {
            System.err.println("Output file must have .zst extension when using zst compression");
            CommandLineOptions.printGenerateHelp();
            throw new GenerateFailure();
        }

        // Legacy behavior for jsonl
        if (!clientParameters.zstCompression && outputFile.endsWith(".zst")) {
            System.err.println("Output file must not have .zst extension when not using zst compression");
            CommandLineOptions.printGenerateHelp();
            throw new GenerateFailure();
        }

        this.outputFile = outputFile;
    }

    private void generate() throws IOException {
        FormatStrategy.Result res = formatStrategy.build();
        Map<String, Long> df = res.termDf();
        long pageCount = res.documentCount();
        String languagesKey = formatStrategy.languageKey();

        // Legacy behavior: drop df == 1 terms
        Map<String, Long> finalDf = df.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        TreeMap::new // keep output sorted
                ));

        System.out.println("Total documents processed: " + pageCount + ", unique words: " + finalDf.size());

        SignificanceModelFile modelFile;
        File outputFile = Paths.get(this.outputFile).toFile();

        if (outputFile.exists()) {
            try (InputStream in = outputFile.toString().endsWith(".zst")
                    ? new ZstdInputStream(new FileInputStream(outputFile))
                    : new FileInputStream(outputFile)) {
                modelFile = objectMapper.readValue(in, SignificanceModelFile.class);
            }
            modelFile.addLanguage(languagesKey, new DocumentFrequencyFile(DOC_FREQ_DESCRIPTION, pageCount, finalDf));
        } else {
            var languagesMap = new HashMap<String, DocumentFrequencyFile>() {{
                put(languagesKey, new DocumentFrequencyFile(DOC_FREQ_DESCRIPTION, pageCount, finalDf));
            }};
            modelFile = new SignificanceModelFile(VERSION, ID, SIGNIFICANCE_DESCRIPTION + " " + clientParameters.inputFile, languagesMap);
        }

        try (OutputStream out = useZstCompression
                ? new ZstdOutputStream(new FileOutputStream(outputFile))
                : new FileOutputStream(outputFile)) {
            ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();
            writer.writeValue(out, modelFile);
        }
    }

    static final class GenerateFailure extends RuntimeException {
    }
}
