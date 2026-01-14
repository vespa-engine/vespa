package com.yahoo.language.process;

import com.yahoo.language.Language;

/**
 * Parameters to a linguistics operation.
 *
 * @param field the field this is tokenizing for (the default field if this is tokenizing for a language having multiple ones)
 * @param query true if this is for queries, false if it is for document indexing
 * @param language the language of the input string.
 * @param stemMode the stem mode applied on the returned tokens
 * @param removeAccents whether to normalize accents and similar
 * @param lowercase whether to lowercase
 * @author bratseth
 */
public record LinguisticsParameters(String field,
                                    boolean query,
                                    Language language,
                                    StemMode stemMode,
                                    boolean removeAccents,
                                    boolean lowercase) {
}
