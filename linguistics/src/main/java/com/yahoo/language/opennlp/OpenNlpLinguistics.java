// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.simple.SimpleLinguistics;

public class OpenNlpLinguistics extends SimpleLinguistics {

    @Override
    public Tokenizer getTokenizer() {
        return new OpenNlpTokenizer(getNormalizer(), getTransformer());
    }

}
