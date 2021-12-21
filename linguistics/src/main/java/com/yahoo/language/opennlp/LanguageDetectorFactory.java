// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import opennlp.tools.langdetect.LanguageDetectorContextGenerator;
import opennlp.tools.util.normalizer.EmojiCharSequenceNormalizer;
import opennlp.tools.util.normalizer.NumberCharSequenceNormalizer;
import opennlp.tools.util.normalizer.ShrinkCharSequenceNormalizer;
import opennlp.tools.util.normalizer.TwitterCharSequenceNormalizer;

/**
 * Overrides the UrlCharSequenceNormalizer, which has a bad regex, until fixed: https://issues.apache.org/jira/browse/OPENNLP-1350
 *
 * @author jonmv
 */
@SuppressWarnings("unused") // Loaded by black magic: specified in properties in the loaded model.
public class LanguageDetectorFactory extends opennlp.tools.langdetect.LanguageDetectorFactory {

    @Override
    public LanguageDetectorContextGenerator getContextGenerator() {
        return new DefaultLanguageDetectorContextGenerator(1, 3,
                                                           EmojiCharSequenceNormalizer.getInstance(),
                                                           UrlCharSequenceNormalizer.getInstance(),
                                                           TwitterCharSequenceNormalizer.getInstance(),
                                                           NumberCharSequenceNormalizer.getInstance(),
                                                           ShrinkCharSequenceNormalizer.getInstance());
    }

}
