// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import com.yahoo.language.Language;
import com.yahoo.language.detect.Detection;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.detect.Hint;
import com.yahoo.language.simple.SimpleDetector;
import opennlp.tools.langdetect.LanguageDetectorConfig;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Detects text language using patched OpenNLP, with fallback to {@link SimpleDetector} for undetected CJK input.
 *
 * @author jonmv
 */
class OpenNlpDetector implements Detector {

    private static final Object monitor = new Object();
    private static LanguageDetectorModel model;

    private final SimpleDetector simple = new SimpleDetector();
    private final Map<String, Language> languagesByISO3 = new HashMap<>();
    private final LanguageDetectorME detector;
    private final LanguageDetectorConfig config;

    OpenNlpDetector() {
        detector = new LanguageDetectorME(loadModel());
        config = new LanguageDetectorConfig();
        config.setMinDiff(0.02);
        config.setChunkSize(32);
        config.setMaxLength(256);
        for (Locale locale : Locale.getAvailableLocales()) {
            Language language = Language.fromLocale(locale);
            if (language != null)
                languagesByISO3.put(locale.getISO3Language(), language);
        }
    }

    private static LanguageDetectorModel loadModel() {
        synchronized (monitor) {
            if (model == null) {
                try {
                    model = new LanguageDetectorModel(OpenNlpDetector.class.getResourceAsStream("/models/langdetect-183.bin"));
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        return model;
    }

    @Override
    public Detection detect(byte[] input, int offset, int length, Hint hint) {
        Charset encoding = Charset.forName(simple.guessEncoding(input, offset, length));
        return new Detection(detectLanguage(new String(input, offset, length, encoding)), encoding.name(), false);
    }

    @Override
    public Detection detect(ByteBuffer input, Hint hint) {
        if (input.hasArray())
            return detect(input.array(), input.arrayOffset() + input.position(), input.remaining(), hint);

        byte[] buffer = new byte[input.remaining()];
        input.get(buffer);
        return detect(buffer, 0, buffer.length, hint);
    }

    @Override
    public Detection detect(String input, Hint hint) {
        return new Detection(detectLanguage(input), UTF_8.name(), false);
    }

    private Language detectLanguage(String input) {
        var prediction = detector.probingPredictLanguages(input, config).getLanguages()[0];
        var result = prediction.getConfidence() > 0.02 ? languagesByISO3.get(prediction.getLang()) : null;
        return result != null ? result : simple.guessLanguage(input.substring(0, Math.min(input.length(), 256)));
    }

}
