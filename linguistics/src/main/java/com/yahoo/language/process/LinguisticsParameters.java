package com.yahoo.language.process;

import com.yahoo.language.Language;

/**
 * Parameters to a linguistics operation.
 *
 * @param profile the linguistics profile this should use in the linguistics module,
 *                or null to use the default
 * @param language the language of the input string.
 * @param stemMode the stem mode applied on the returned tokens
 * @param removeAccents whether to normalize accents and similar
 * @param lowercase whether to lowercase
 * @author bratseth
 */
public record LinguisticsParameters(String profile,
                                    Language language,
                                    StemMode stemMode,
                                    boolean removeAccents,
                                    boolean lowercase) {
}
