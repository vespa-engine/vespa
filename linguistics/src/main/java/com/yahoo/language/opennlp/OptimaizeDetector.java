// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import com.google.common.base.Optional;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObjectFactory;
import com.yahoo.language.Language;
import com.yahoo.language.detect.Detection;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.detect.Hint;
import com.yahoo.language.simple.SimpleDetector;
import com.yahoo.text.Utf8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

/**
 * Detects the language of some sample text using SimpleDetector for CJK and Optimaize otherwise.
 *
 * @author bratseth
 */
public class OptimaizeDetector implements Detector {

    static private Object initGuard = new Object();
    static private TextObjectFactory textObjectFactory = null;
    static private LanguageDetector languageDetector = null;

    static private void initOptimaize() {
        synchronized (initGuard) {
            if ((textObjectFactory != null) && (languageDetector != null)) return;

            // origin: https://github.com/optimaize/language-detector
            // load all languages:
            List<LanguageProfile> languageProfiles;
            try {
                languageProfiles = new LanguageProfileReader().readAllBuiltIn();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            //build language detector:
            languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                                                      .withProfiles(languageProfiles)
                                                      .build();

            //create a text object factory
            textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();
        }
    }

    private SimpleDetector simpleDetector = new SimpleDetector();

    public OptimaizeDetector() {
        initOptimaize();
    }

    @Override
    public Detection detect(byte[] input, int offset, int length, Hint hint) {
        return new Detection(guessLanguage(input, offset, length), simpleDetector.guessEncoding(input), false);
    }

    @Override
    public Detection detect(ByteBuffer input, Hint hint) {
        byte[] buf = new byte[input.remaining()];
        input.get(buf, 0, buf.length);
        return detect(buf, 0, buf.length, hint);
    }

    @Override
    public Detection detect(String input, Hint hint) {
        return new Detection(guessLanguage(input), Utf8.getCharset().name(), false);
    }

    private Language guessLanguage(byte[] buf, int offset, int length) {
        return guessLanguage(Utf8.toString(buf, offset, length));
    }

    public Language guessLanguage(String input) {
        if (input == null || input.length() == 0) return Language.UNKNOWN;

        Language result = simpleDetector.guessLanguage(input);
        if (result != Language.UNKNOWN) return result;

        return guessLanguageUsingOptimaize(input);
    }

    private static Language guessLanguageUsingOptimaize(String input) {
        Optional<LdLocale> result = languageDetector.detect(textObjectFactory.forText(input));
        if ( ! result.isPresent()) return Language.UNKNOWN;

        return Language.fromLocale(new Locale(result.get().getLanguage()));
    }

}
