// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import com.yahoo.language.Language;
import com.yahoo.language.detect.Detection;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.detect.Hint;
import com.yahoo.language.simple.SimpleDetector;
import opennlp.tools.cmdline.langdetect.LanguageDetectorModelLoader;
import opennlp.tools.langdetect.LanguageDetectorConfig;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Detects the language of some sample text using {@link SimpleDetector} for CJK input, and OpenNLP otherwise.
 *
 * @author jonmv
 */
class OpenNlpDetector implements Detector {

    private final SimpleDetector simple = new SimpleDetector();
    private final Map<String, Language> languagesByISO3 = new HashMap<>();
    private final LanguageDetectorME detector;
    private final LanguageDetectorConfig config;

    OpenNlpDetector(LanguageDetectorModel model) {
        detector = new LanguageDetectorME(model);
        config = new LanguageDetectorConfig();
        config.setMinDiff(0.02);
        config.setChunkSize(64);
        for (Locale locale : Locale.getAvailableLocales())
            languagesByISO3.put(locale.getISO3Language(), Language.fromLocale(locale));
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
        Language simpleGuess = simple.guessLanguage(input);
        if (simpleGuess != Language.UNKNOWN)
            return simpleGuess;

        var prediction = detector.probingPredictLanguages(input, config).getLanguages()[0];
        return prediction.getConfidence() > 0.03 ? languagesByISO3.getOrDefault(prediction.getLang(), Language.UNKNOWN)
                                                 : Language.UNKNOWN;
    }

}
