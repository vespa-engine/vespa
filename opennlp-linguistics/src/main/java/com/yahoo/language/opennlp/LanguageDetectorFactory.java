// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import opennlp.tools.langdetect.LanguageDetectorContextGenerator;

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
                                                           VespaCharSequenceNormalizer.getInstance());
    }

}
