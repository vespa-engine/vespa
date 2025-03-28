package com.yahoo.language.process;

import com.yahoo.language.Language;

/**
 * Parameters to a linguistics operation.
 *
 * @param language the language of the input string.
 * @param stemMode the stem mode applied on the returned tokens
 * @param removeAccents whether to normalize accents and similar
 * @param lowercase whether to lowercase
 * @author bratseth
 */
public record LinguisticsParameters(Language language,
                                    StemMode stemMode,
                                    boolean removeAccents,
                                    boolean lowercase) {
}
