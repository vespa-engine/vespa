// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.lucene;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.Language;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenScript;
import com.yahoo.language.process.TokenType;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleToken;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author dainiusjocas
 */
class LuceneTokenizer implements Tokenizer {

    private static final Logger log = Logger.getLogger(LuceneTokenizer.class.getName());

    // Dummy value, just to stuff the Lucene interface.
    private final static String FIELD_NAME = "F";

    private final AnalyzerFactory analyzerFactory;

    public LuceneTokenizer(LuceneAnalysisConfig config) {
        this(config, new ComponentRegistry<>());
    }
    public LuceneTokenizer(LuceneAnalysisConfig config, ComponentRegistry<Analyzer> analyzers) {
        this.analyzerFactory = new AnalyzerFactory(config, analyzers);
    }

    @Override
    public Iterable<Token> tokenize(String input, Language language, StemMode stemMode, boolean removeAccents) {
        if (input.isEmpty()) return List.of();

        List<Token> tokens = textToTokens(input, analyzerFactory.getAnalyzer(language, stemMode, removeAccents));
        log.log(Level.FINEST, () -> "Tokenized '" + language + "' text='" + input + "' into: n=" + tokens.size() + ", tokens=" + tokens);
        return tokens;
    }

    private List<Token> textToTokens(String text, Analyzer analyzer) {
        List<Token> tokens = new ArrayList<>();
        TokenStream tokenStream = analyzer.tokenStream(FIELD_NAME, text);

        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
        try {
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                // TODO: what to do with cases when multiple tokens are inserted into the position?
                String originalString = text.substring(offsetAttribute.startOffset(), offsetAttribute.endOffset());
                String tokenString = charTermAttribute.toString();
                tokens.add(new SimpleToken(originalString, tokenString)
                        .setType(TokenType.ALPHABETIC)
                        .setOffset(offsetAttribute.startOffset())
                        .setScript(TokenScript.UNKNOWN));
            }
            tokenStream.end();
            tokenStream.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to analyze: " + text, e);
        }
        return tokens;
    }

}
