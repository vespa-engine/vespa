// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.lucene;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.LinguisticsParameters;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenType;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleToken;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

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

    private final Analyzers analyzers;

    public LuceneTokenizer(LuceneAnalysisConfig config) {
        this(config, new ComponentRegistry<>());
    }
    public LuceneTokenizer(LuceneAnalysisConfig config, ComponentRegistry<Analyzer> analyzerComponents) {
        this.analyzers = new Analyzers(config, analyzerComponents);
    }

    @Override
    public Iterable<Token> tokenize(String input, LinguisticsParameters parameters) {
        if (input.isEmpty()) return List.of();

        List<Token> tokens = textToTokens(input, analyzers.getAnalyzer(parameters));
        log.log(Level.FINEST, () -> "Tokenized '" + parameters.language() +
                                    "' text='" + input + "' into: n=" + tokens.size() + ", tokens=" + tokens);
        return tokens;
    }

    private List<Token> textToTokens(String text, Analyzer analyzer) {
        List<Token> tokens = new ArrayList<>();
        TokenStream tokenStream = analyzer.tokenStream(FIELD_NAME, text);

        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
        PositionIncrementAttribute posIncAttribute = tokenStream.addAttribute(PositionIncrementAttribute.class);
        try {
            tokenStream.reset();
            SimpleToken current = null;
            while (tokenStream.incrementToken()) {
                String originalString = text.substring(offsetAttribute.startOffset(), offsetAttribute.endOffset());
                String tokenString = charTermAttribute.toString();
                if (isAtSamePosition(current, posIncAttribute)) {
                    current.addStem(tokenString);
                }
                else {
                    current = new SimpleToken(originalString, tokenString).setType(TokenType.ALPHABETIC)
                                                                          .setOffset(offsetAttribute.startOffset());
                    tokens.add(current);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to analyze: " + text, e);
        } finally {
            try {
                tokenStream.end();
                tokenStream.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close stream: " + e);
            }
        }
        return tokens;
    }

    private boolean isAtSamePosition(Token token, PositionIncrementAttribute posIncAttribute) {
        if (token == null) return false;
        return posIncAttribute.getPositionIncrement() == 0;
    }

    @Override
    public boolean equals(Object other) {
        // Config actually determines if Linguistics are equal
        return (other instanceof LuceneTokenizer) && analyzers.equals(((LuceneTokenizer) other).analyzers);
    }

    @Override
    public int hashCode() {
        return analyzers.hashCode();
    }

}
