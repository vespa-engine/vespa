// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
package ai.vespa.vespasignificance.generate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.language.Language;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenScript;
import com.yahoo.language.process.TokenType;
import com.yahoo.language.process.Tokenizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Builds a document-frequency (DF) map from a JSON Lines (JSONL) file containing Vespa feed documents.
 *
 * @author johsol
 */
public final class JsonlDocumentFormatStrategy implements FormatStrategy {

    private final Path input;
    private final String field;
    private final Tokenizer tokenizer;
    private final Language tokenizationLanguage;
    private final List<Language> languageKeyParts;

    private final ObjectMapper mapper = new ObjectMapper();

    public JsonlDocumentFormatStrategy(Path input,
                                       Tokenizer tokenizer,
                                       Language tokenizationLanguage,
                                       List<Language> languageKeyParts,
                                       String field) {
        this.input = input;
        this.field = field;
        this.tokenizer = tokenizer;
        this.tokenizationLanguage = tokenizationLanguage;
        this.languageKeyParts = languageKeyParts;
    }

    @Override
    public Result build() throws IOException {
        SortedMap<String, Long> df = new TreeMap<>();
        long docs = 0;

        try (var br = Files.newBufferedReader(input)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    docs++;
                    continue;
                }

                JsonNode root = mapper.readTree(line);
                JsonNode fields = root.get("fields");
                if (fields != null) {
                    JsonNode value = fields.get(field);
                    if (value != null && !value.isNull()) {
                        // Legacy parity: if it's a string, use it; otherwise tokenize the JSON rendering
                        String text = value.isTextual() ? value.asText() : value.toString();
                        tokenizeAndAccumulate(df, text);
                    }
                }

                docs++;
                if (docs % 50_000 == 0) {
                    System.out.println("Documents processed: " + docs + ", unique terms: " + df.size());
                }
            }
        }

        return new Result(Collections.unmodifiableSortedMap(new TreeMap<>(df)), docs);
    }

    private void tokenizeAndAccumulate(SortedMap<String, Long> df, String text) {
        var params = new LinguisticsParameters(tokenizationLanguage, StemMode.NONE, false, true);
        var tokens = tokenizer.tokenize(text, params);

        var unique = new java.util.HashSet<String>();
        for (Token t : tokens) {
            if (t.getType() == TokenType.ALPHABETIC && t.getScript() == TokenScript.LATIN) {
                unique.add(t.getTokenString()); // legacy: no explicit lowercasing/normalization
            }
        }
        for (String term : unique) {
            df.merge(term, 1L, Long::sum);
        }
    }

    @Override
    public String languageKey() {
        return String.join(",", languageKeyParts.stream().map(Language::languageCode).toList());
    }
}
