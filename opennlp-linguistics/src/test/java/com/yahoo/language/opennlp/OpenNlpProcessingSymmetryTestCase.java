package com.yahoo.language.opennlp;

import ai.vespa.opennlp.OpenNlpConfig;
import com.yahoo.language.Language;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OpenNlpProcessingSymmetryTestCase {

    @Test
    public void testSymmetricTransformation() {
        var tester = new OpenNlpLinguisticsTester();
        var input = "conges";
        String indexed = tester.tokenizeToString(input, Language.ENGLISH);
        String queried = tester.stemAndNormalize(input, Language.ENGLISH);
        assertEquals("Expected that the actual query token equals the indexed", indexed, queried);
    }

    @Test
    public void testSymmetricTransformationWithAccentsEnglishKStem() {
        var tester = new OpenNlpLinguisticsTester();
        var input = "congés";
        String indexed = tester.tokenizeToString(input, Language.ENGLISH);
        String queried = tester.stemAndNormalize(input, Language.ENGLISH);
        assertEquals("Expected that the actual query token equals the indexed", indexed, queried);
    }

    @Test
    public void testSymmetricTransformationWithAccentsEnglishSnowball() {
        var tester = new OpenNlpLinguisticsTester(new OpenNlpConfig.Builder().snowballStemmingForEnglish(true).build());
        var input = "congés";
        String indexed = tester.tokenizeToString(input, Language.ENGLISH);
        String queried = tester.stemAndNormalize(input, Language.ENGLISH);
        assertEquals("Expected that the actual query token equals the indexed", indexed, queried);
    }

    @Test
    public void testSymmetricTransformationWithAccentsSpanish() {
        var tester = new OpenNlpLinguisticsTester();
        var input = "congés";
        String indexed = tester.tokenizeToString(input, Language.SPANISH);
        String queried = tester.stemAndNormalize(input, Language.SPANISH);
        assertEquals("Expected that the actual query token equals the indexed", indexed, queried);
    }

}
