// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.Case;
import com.yahoo.schema.document.Dictionary;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Propagates dictionary and match casing settings from field level to attribute level, and verifies that the
 * two agree. Numeric fields need fast-search for dictionary control, and do not use casing at all.
 *
 * @author baldersheim
 */
public class DictionaryProcessor extends Processor {

    public DictionaryProcessor(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFieldsWithSubFields()) {
            Attribute attribute = field.getAttribute();
            if (attribute == null) continue;
            boolean numeric = attribute.getDataType().getPrimitiveType() instanceof NumericDataType;
            // Only string attributes are matched cased or uncased, so a numeric one keeps the default.
            attribute.setCase(numeric ? Case.UNCASED : field.getMatching().getCase());
            Dictionary dictionary = field.getDictionary();
            if (numeric) {
                dictionary = withoutCasing(schema, field, dictionary, validate);
            } else if (dictionary == null) {
                dictionary = impliedDictionary(field, attribute);
            }
            if (dictionary == null) continue;
            if (numeric) {
                if (attribute.isFastSearch()) {
                    attribute.setDictionary(dictionary);
                } else {
                    fail(schema, field, "You must specify 'attribute:fast-search' to allow dictionary control");
                }
            } else if (attribute.getDataType().getPrimitiveType() == PrimitiveDataType.STRING) {
                attribute.setDictionary(dictionary);
                // A hash dictionary can not be folded, so any dictionary with a hash part requires cased match.
                if (dictionary.getType() != Dictionary.Type.BTREE && dictionary.getMatch() != Case.CASED) {
                    fail(schema, field, "hash dictionary require cased match");
                }
                if (! dictionary.getMatch().equals(attribute.getCase())) {
                    fail(schema, field, "Dictionary casing '" + dictionary.getMatch() + "' does not match field match casing '" + attribute.getCase() + "'");
                }
            } else {
                fail(schema, field, "You can only specify 'dictionary:' for numeric or string fields");
            }
        }
    }

    /**
     * Returns the dictionary settings of a numeric field with any casing dropped, or null if it has no
     * dictionary settings left after that.
     *
     * Casing only decides how strings are compared: a numeric attribute is never matched cased or uncased,
     * and its dictionary is never folded. Setting either on a numeric field is therefore ignored, with a
     * warning, rather than silently producing config that has no effect.
     */
    private Dictionary withoutCasing(Schema schema, SDField field, Dictionary dictionary, boolean validate) {
        boolean casingSet = dictionary != null && dictionary.isMatchSet();
        if (validate) {
            // 'match: cased' also sets the dictionary casing, so report whichever setting the user wrote.
            if (field.getMatching().isCaseUserSet()) {
                warn(schema, field, "'match: " + field.getMatching().getCase().getName() +
                                    "' is only used for string fields, ignoring it.");
            } else if (casingSet) {
                warn(schema, field, "'dictionary: " + dictionary.getMatch().getName() +
                                    "' is only used for string fields, ignoring it.");
            }
        }
        if ( ! casingSet) return dictionary;
        if ( ! dictionary.isTypeSet()) return null; // Nothing but the casing was set
        Dictionary withoutCasing = new Dictionary();
        withoutCasing.updateType(dictionary.getType());
        return withoutCasing;
    }

    /**
     * Returns the dictionary implied by the match casing of a field which has no dictionary of its own,
     * or null if the default dictionary settings are already right for it.
     *
     * Cased matching must be reflected in the dictionary: the dictionary casing decides how the enum store is
     * ordered and whether the case variants of a value share a single posting list, while the field level match
     * casing only decides how the query side compares terms. If the two disagree, a cased search on a fast-search
     * attribute matches the folded posting lists of the dictionary and returns the wrong documents.
     *
     * A struct field inherits its match casing from the enclosing field (see SDField.setMatchingCase), which does
     * not carry a dictionary along, so the dictionary has to be derived from the casing here.
     */
    private static Dictionary impliedDictionary(SDField field, Attribute attribute) {
        if (attribute.getCase() != Case.CASED) return null;
        if (attribute.getDataType().getPrimitiveType() != PrimitiveDataType.STRING) return null;
        Dictionary dictionary = field.getOrSetDictionary();
        dictionary.updateMatch(Case.CASED);
        return dictionary;
    }

}
