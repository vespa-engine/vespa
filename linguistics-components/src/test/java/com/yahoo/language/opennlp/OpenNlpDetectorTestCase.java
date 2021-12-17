// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import com.yahoo.language.Language;
import com.yahoo.language.detect.Detector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class OpenNlpDetectorTestCase {

    @Test
    public void testDetection() {
        Detector detector = new OpenNlpDetector(new LangDetectModel183().load());

        assertLanguage(Language.UNKNOWN,
                       "",
                       detector);

        assertLanguage(Language.UNKNOWN,
                       "Hello!",
                       detector);

        // from https://en.wikipedia.org/wiki/Yahoo
        assertLanguage(Language.ENGLISH,
                       "Yahoo became a public company via an initial public offering in April 1996 and its stock price rose 600% within two years.",
                       detector);

        // from https://de.wikipedia.org/wiki/Yahoo
        assertLanguage(Language.GERMAN,
                       "1996 ging Yahoo mit 46 Angestellten an die Börse. 2009 arbeiteten insgesamt rund 13.500 Mitarbeiter für Yahoo.",
                       detector);

        // from https://fr.wikipedia.org/wiki/Yahoo
        assertLanguage(Language.FRENCH,
                       "À l'origine, Yahoo! était uniquement un annuaire Web.",
                       detector);

        // Test fallback to SimpleDetector
        assertLanguage(Language.CHINESE_TRADITIONAL, // CHINESE_SIMPLIFIED input
                       "\u6211\u80FD\u541E\u4E0B\u73BB\u7483\u800C\u4E0D\u4F24\u8EAB\u4F53\u3002",
                       detector);

        // from https://ru.wikipedia.org/wiki/%D0%A0%D0%BE%D1%81%D1%81%D0%B8%D1%8F
        assertLanguage(Language.RUSSIAN,
                       "7 февраля 2000 года Yahoo.com подвергся DDoS атаке и на несколько часов приостановил работу.",
                       detector);

        // https://he.wikipedia.org/wiki/Yahoo!
        assertLanguage(Language.HEBREW,
                       "אתר יאהו! הוא אחד מאתרי האינטרנט הפופולריים ביותר בעולם, עם מעל 500 מיליון כניסות בכל יום",
                       detector);
    }

    private void assertLanguage(Language language, String input, Detector detector) {
        assertEquals(language, detector.detect(input, null).getLanguage());
    }

}
