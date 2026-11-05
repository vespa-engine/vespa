// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.ComplexAttributeFieldUtils;
import com.yahoo.schema.document.ComplexIndexFieldUtils;
import com.yahoo.schema.document.GeoPos;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.MatchType;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.indexinglanguage.expressions.ExactExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.ForEachFieldExpression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.PassthroughExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.expressions.TokenizeExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.ArrayList;

/**
 * Sets the struct field indexing script of complex fields having supported struct field indexes:
 * "input field | for_each_field "f1" { tokenize ... } | ... | passthrough field",
 * which annotates the string values of the indexed struct fields in place, so that the backend can index them,
 * while passing the complex field through unchanged otherwise.
 * The struct fields are named by their path in the complex field, e.g "f1", "key" or "value.f1".
 * This script is only used in indexed mode, as streaming search tokenizes struct fields itself.
 *
 * @author arnej
 */
public class StructFieldIndexing extends Processor {

    public StructFieldIndexing(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        var mode = ComplexIndexFieldUtils.Mode.of(schema);
        for (SDField field : schema.allConcreteFields()) {
            boolean hasStructFields = field.usesStructOrMap() && ! GeoPos.isAnyPos(field);
            field.setStructFieldIndexingScript(hasStructFields ? structFieldIndexingScript(field, mode) : null);
        }
    }

    private ScriptExpression structFieldIndexingScript(SDField field, ComplexIndexFieldUtils.Mode mode) {
        var structFields = ComplexIndexFieldUtils.supportedIndexedStructFields(field, mode);
        if (structFields.isEmpty()) return new ScriptExpression();
        var expressions = new ArrayList<Expression>();
        expressions.add(new InputExpression(field.getName()));
        for (ImmutableSDField structField : structFields) {
            expressions.add(new ForEachFieldExpression(ComplexAttributeFieldUtils.localStructFieldName(field, structField),
                                                       annotateExpression(structField)));
        }
        expressions.add(new PassthroughExpression(field.getName()));
        return new ScriptExpression(new StatementExpression(expressions));
    }

    private Expression annotateExpression(ImmutableSDField structField) {
        MatchType matchType = structField.getMatching().getType();
        if (matchType == MatchType.EXACT || matchType == MatchType.WORD)
            return new ExactExpression(ExactMatch.toAnnotatorConfig(structField.getMatching()));
        return new TokenizeExpression(null, TextMatch.findAnnotatorConfig(schema, structField));
    }

}
