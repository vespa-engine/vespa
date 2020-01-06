// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import com.yahoo.language.Language;
import com.yahoo.language.detect.Detector;
import com.yahoo.language.simple.SimpleDetector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class OptimaizeDetectorTestCase {

    private static final Detector detector = new OptimaizeDetector();

    @Test
    public void testDetection() {
        assertLanguage(Language.UNKNOWN, "Hello!");

        // Test fallback to SimpleDetector
        assertLanguage(Language.CHINESE_TRADITIONAL, // CHINESE_SIMPLIFIED input
                       "\u6211\u80FD\u541E\u4E0B\u73BB\u7483\u800C\u4E0D\u4F24\u8EAB\u4F53\u3002");

        // from https://ru.wikipedia.org/wiki/%D0%A0%D0%BE%D1%81%D1%81%D0%B8%D1%8F
        assertLanguage(Language.RUSSIAN, "Материал из Википедии — свободной энциклопедии");
        // https://he.wikipedia.org/wiki/Yahoo!
        assertLanguage(Language.HEBREW, "אתר יאהו! הוא אחד מאתרי האינטרנט הפופולריים ביותר בעולם, עם מעל 500 מיליון כניסות בכל יום");
    }

    private static void assertLanguage(Language language, String input) {
        assertEquals(language, detector.detect(input, null).getLanguage());
    }

}
