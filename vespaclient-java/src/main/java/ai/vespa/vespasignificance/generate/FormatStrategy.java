// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.generate;

import java.io.IOException;
import java.util.SortedMap;

/**
 * Build document frequency map from input format.
 *
 * @author johsol
 */
public interface FormatStrategy {

    /** Returns a map from term to document frequency */
    Result build() throws IOException;

    /** Language key for {@link com.yahoo.language.significance.SignificanceModel} */
    String languageKey();

    record Result(SortedMap<String, Long> termDf, long documentCount) {}
}
