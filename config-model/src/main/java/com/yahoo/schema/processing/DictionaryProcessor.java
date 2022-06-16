// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
 * Propagates dictionary settings from field level to attribute level.
 * Only applies to numeric fields with fast-search enabled.
 *
 * @author baldersheim
 */
public class DictionaryProcessor extends Processor {

    public DictionaryProcessor(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (SDField field : schema.allConcreteFields()) {
            Attribute attribute = field.getAttribute();
            if (attribute == null) continue;
            attribute.setCase(field.getMatching().getCase());
            Dictionary dictionary = field.getDictionary();
            if (dictionary == null) continue;
            if (attribute.getDataType().getPrimitiveType() instanceof NumericDataType ) {
                if (attribute.isFastSearch()) {
                    attribute.setDictionary(dictionary);
                } else {
                    fail(schema, field, "You must specify 'attribute:fast-search' to allow dictionary control");
                }
            } else if (attribute.getDataType().getPrimitiveType() == PrimitiveDataType.STRING) {
                attribute.setDictionary(dictionary);
                if (dictionary.getType() == Dictionary.Type.HASH) {
                    if (dictionary.getMatch() != Case.CASED) {
                        fail(schema, field, "hash dictionary require cased match");
                    }
                }
                if (! dictionary.getMatch().equals(attribute.getCase())) {
                    fail(schema, field, "Dictionary casing '" + dictionary.getMatch() + "' does not match field match casing '" + attribute.getCase() + "'");
                }
            } else {
                fail(schema, field, "You can only specify 'dictionary:' for numeric or string fields");
            }
        }
    }

}
