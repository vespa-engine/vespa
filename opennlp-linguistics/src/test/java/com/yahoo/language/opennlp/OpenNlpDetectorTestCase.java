// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import com.yahoo.language.Language;
import com.yahoo.language.detect.Detector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class OpenNlpDetectorTestCase {

    Detector detector = new OpenNlpDetector();
    Detector wildGuess = new OpenNlpDetector(1.0);
    Detector certain = new OpenNlpDetector(10.0);

    @Test
    public void testDetection() {

        assertLanguage(Language.UNKNOWN,
                       "");

        assertLanguage(Language.UNKNOWN,
                       "Hello!");

        // NOTE: threshold 1.0 makes for wild guesses:
        assertGuess(Language.MALAY, "Hello!");
        assertGuess(Language.DUTCH, "Je m'appelle Camille");
        // even threshold 2.0 is problematic for some words:
        assertLanguage(Language.DUTCH, "School and kindergarten damaged in reported strike");
        // threshold 10.0 is more likely to say "unknown":
        assertEquals(Language.UNKNOWN, certain.detect("School and kindergarten damaged in reported strike", null).getLanguage());

        // from https://no.wikipedia.org/
        assertCertain(Language.NORWEGIAN_BOKMAL,
"""
en også kalt eller bare portal er en type nettsted som lenker til andre
og fra flere kilder på en ensartet måte portaler fungerer som en inngang
til en rekke andre ressurser og sider innen et visst eller rettet mot en
""");

        // from https://en.wikipedia.org/wiki/Yahoo
        assertLanguage(Language.ENGLISH,
                       "Yahoo became a public company via an initial public offering in April 1996 and its stock price rose 600% within two years.");

        // from https://de.wikipedia.org/wiki/Yahoo
        assertLanguage(Language.GERMAN,
                       "1996 ging Yahoo mit 46 Angestellten an die Börse. 2009 arbeiteten insgesamt rund 13.500 Mitarbeiter für Yahoo.");

        assertGuess(Language.GERMAN, "Eine kleine Nachtmusik");

        // from https://fr.wikipedia.org/wiki/Yahoo
        assertLanguage(Language.FRENCH,
                       "À l'origine, Yahoo! était uniquement un annuaire Web.");

        assertGuess(Language.FRENCH, "Je m'appelle La Fontaine");

        // Test fallback to SimpleDetector
        assertLanguage(Language.CHINESE_TRADITIONAL, // CHINESE_SIMPLIFIED input
                       "我能吞下玻璃而不伤身体。");

        // from https://zh.wikipedia.org/wiki/Yahoo
        assertLanguage(Language.CHINESE_TRADITIONAL,
                       "Yahoo! Next是一个展示雅虎新技术、新产品的场所，目前在测试阶段。");

        // from https://th.wikipedia.org/wiki/Yahoo
        assertLanguage(Language.THAI,
                       "เดือนกรกฎาคม 2012 Yahoo! ก็ได้ประธานเจ้าหน้าที่บริหารคนใหม่ \"มาริสสา เมเยอร์\" อดีตผู้บริหารจาก Google มาทำหน้าที่พลิกฟื้นบริษัท");

        // from https://ar.wikipedia.org/wiki/Yahoo
        assertCertain(Language.ARABIC,
                       "وفقًا لمزودي تحليلات الويب دائما كأليكسا وسميلارويب،وصل موقع ياهولأكثر من 7 مليارات مشاهدة شهريًا - حيث احتل المرتبة السادسة بين أكثر مواقع الويب زيارة على مستوى العالم في عام 2016.");

        // from https://ko.wikipedia.org/wiki/Yahoo
        assertCertain(Language.KOREAN,
                      "야후!의 전신인 디렉터리 사이트는 1994년 1월에 스탠퍼드 대학교 출신의 제리 양과 데이비드 파일로가 만들었으며, 회사는 1995년 3월 2일에 설립되었다.");

        // from https://ja.wikipedia.org/wiki/Yahoo
        assertLanguage(Language.JAPANESE,
                       "日本では、ヤフー株式会社がYahoo!（後にベライゾンがアルタバに売却）とソフトバンクの合弁会社として1996年に設立した。");

        // from https://ru.wikipedia.org/wiki/Yahoo
        assertLanguage(Language.RUSSIAN,
                       "7 февраля 2000 года Yahoo.com подвергся DDoS атаке и на несколько часов приостановил работу.");
        // from https://he.wikipedia.org/wiki/Yahoo
        assertLanguage(Language.HEBREW,
                       "אתר יאהו! הוא אחד מאתרי האינטרנט הפופולריים ביותר בעולם, עם מעל 500 מיליון כניסות בכל יום");
    }

    private void assertLanguage(Language language, String input) {
        assertEquals(language, detector.detect(input, null).getLanguage());
    }

    private void assertGuess(Language language, String input) {
        assertEquals(language, wildGuess.detect(input, null).getLanguage());
        assertEquals(Language.UNKNOWN, detector.detect(input, null).getLanguage());
        assertEquals(Language.UNKNOWN, certain.detect(input, null).getLanguage());
    }

    private void assertCertain(Language language, String input) {
        assertEquals(language, wildGuess.detect(input, null).getLanguage());
        assertEquals(language, detector.detect(input, null).getLanguage());
        assertEquals(language, certain.detect(input, null).getLanguage());
    }

}
