// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import opennlp.tools.langdetect.LanguageDetectorModel;

import java.io.IOException;
import java.io.UncheckedIOException;

public class LangDetectModel183 implements LangDetectModel {

    private final Object monitor = new Object();
    private LanguageDetectorModel loaded;

    @Override
    public LanguageDetectorModel load() {
        synchronized (monitor) {
            if (loaded == null) {
                try {
                    loaded = new LanguageDetectorModel(LangDetectModel183.class.getResourceAsStream("/models/langdetect-183.bin"));
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        return loaded;
    }

}
