// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.OutputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.application.validation.change.VespaReindexAction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Validates the indexing script changes in all fields in the current and next search model.
 *
 * @author geirst
 */
public class IndexingScriptChangeValidator {

    private final ClusterSpec.Id id;
    private final Schema currentSchema;
    private final Schema nextSchema;
    private final boolean currentStreaming;
    private final boolean nextStreaming;

    public IndexingScriptChangeValidator(ClusterSpec.Id id, Schema currentSchema, Schema nextSchema) {
        this(id, currentSchema, false, nextSchema, false);
    }

    public IndexingScriptChangeValidator(ClusterSpec.Id id,
                                         Schema currentSchema, boolean currentStreaming,
                                         Schema nextSchema, boolean nextStreaming) {
        this.id = id;
        this.currentSchema = currentSchema;
        this.nextSchema = nextSchema;
        this.currentStreaming = currentStreaming;
        this.nextStreaming = nextStreaming;
    }

    public List<VespaConfigChangeAction> validate() {
        List<VespaConfigChangeAction> result = new ArrayList<>();
        for (ImmutableSDField nextField : new LinkedHashSet<>(nextSchema.allConcreteFields())) {
            String fieldName = nextField.getName();
            ImmutableSDField currentField = currentSchema.getConcreteField(fieldName);
            if (currentField != null) {
                validateScripts(currentField, nextField).ifPresent(result::add);
            }
            else if (nextField.isExtraField()) {
                result.add(VespaReindexAction.of(id,
                                                 null,
                                                 "Non-document field '" + nextField.getName() +
                                                 "' added; this may be populated by reindexing"));
            }
        }
        return result;
    }

    private Optional<VespaConfigChangeAction> validateScripts(ImmutableSDField currentField, ImmutableSDField nextField) {
        boolean changed = false;
        ChangeMessageBuilder messageBuilder = new ChangeMessageBuilder(nextField.getName());
        var changeMessageBuilder = new IndexingScriptChangeMessageBuilder(currentSchema, currentField, nextSchema, nextField);
        ScriptExpression currentScript = currentField.getIndexingScript();
        ScriptExpression nextScript = nextField.getIndexingScript();
        if ( ! equalScripts(currentScript, nextScript)) {
            changeMessageBuilder.populate(messageBuilder);
            messageBuilder.addChange("indexing script", currentScript.toString(), nextScript.toString());
            changed = true;
        }
        // Struct fields with 'indexing: index' are indexed by a script derived for the complex field as a whole
        ScriptExpression currentStructFieldScript = structFieldIndexingScript(currentField, currentStreaming);
        ScriptExpression nextStructFieldScript = structFieldIndexingScript(nextField, nextStreaming);
        if ( ! currentStructFieldScript.equals(nextStructFieldScript)) {
            ChangeMessageBuilder structFieldMessageBuilder = new ChangeMessageBuilder(nextField.getName());
            changeMessageBuilder.populateStructFieldIndexes(structFieldMessageBuilder);
            if (structFieldMessageBuilder.isEmpty()) // a change not described by the message builder
                messageBuilder.addChange("struct field indexing script",
                                         currentStructFieldScript.toString(), nextStructFieldScript.toString());
            else
                messageBuilder.addChanges(structFieldMessageBuilder);
            changed = true;
        }
        if ( ! changed) return Optional.empty();
        return Optional.of(VespaReindexAction.of(id, ValidationId.indexingChange, messageBuilder.build()));
    }

    /** Returns the struct field indexing script which is used for the given field, which is none in streaming mode. */
    private static ScriptExpression structFieldIndexingScript(ImmutableSDField field, boolean streaming) {
        if (streaming) return new ScriptExpression();
        return field.getStructFieldIndexingScript();
    }

    static boolean equalScripts(ScriptExpression currentScript, ScriptExpression nextScript) {
        // Output expressions are specifying in which context a field value is used (attribute, index, summary),
        // and do not affect how the field value is generated in the indexing doc proc.
        // The output expressions are therefore removed before doing the comparison.
        // Validating the addition / removal of attribute and index aspects are handled in other validators.
        return removeOutputExpressions(currentScript).equals(removeOutputExpressions(nextScript));
    }

    private static Expression removeOutputExpressions(ScriptExpression script) {
        return new OutputExpressionRemover().convert(script);
    }

    private static class OutputExpressionRemover extends ExpressionConverter {

        @Override
        protected boolean shouldConvert(Expression exp) {
            return exp instanceof OutputExpression;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            if (exp instanceof OutputExpression) {
                return null;
            }
            return exp;
        }
    }

}
