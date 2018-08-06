package com.yahoo.language.opennlp;

import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleLinguistics;

public class OpenNlpLinguistics extends SimpleLinguistics {
    @Override
    public Tokenizer getTokenizer() {
        return new OpenNlpTokenizer(getNormalizer(), getTransformer());
    }
}
