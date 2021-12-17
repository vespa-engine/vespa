// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import opennlp.tools.langdetect.LanguageDetectorModel;

/**
 * Wrapper to lazily load a langdetect model for OpenNLP.
 *
 * @author jonmv
 */
public interface LangDetectModel {

    /** Loads a {@link LanguageDetectorModel}, or throws if this fails. */
    LanguageDetectorModel load();

}
