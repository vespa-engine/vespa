// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.document.Matching;
import com.yahoo.searchdefinition.document.MatchType;
import com.yahoo.document.NumericDataType;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Fields that derive to attribute(s) and no indices should use the WORD indexing form,
 * in a feeble attempt to match the most peoples expectations as closely as possible.
 *
 * @author Vegard Havdal
 */
public class AttributesImplicitWord extends Processor {

    public AttributesImplicitWord(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        for (ImmutableSDField field : schema.allConcreteFields()) {
            processFieldRecursive(field);
        }
    }

    private void processFieldRecursive(ImmutableSDField field) {
        processField(field);
        for (ImmutableSDField structField : field.getStructFields()) {
            processFieldRecursive(structField);
        }
    }

    private void processField(ImmutableSDField field) {
        if (fieldImplicitlyWordMatch(field)) {
            field.getMatching().setType(MatchType.WORD);
        }
    }

    private boolean fieldImplicitlyWordMatch(ImmutableSDField field) {
        // numeric types should not trigger exact-match query parsing
        DataType dt = field.getDataType().getPrimitiveType();
        if (dt != null && dt instanceof NumericDataType) {
            return false;
        }
        return (! field.hasIndex()
                && !field.getAttributes().isEmpty()
                && field.getIndices().isEmpty()
                && !field.getMatching().isTypeUserSet());
    }

}
