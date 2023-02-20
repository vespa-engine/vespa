// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.Schema;
import com.yahoo.schema.document.GeoPos;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig.Ilscript.Builder;
import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.expressions.ClearStateExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.GuardExpression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.OutputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.PassthroughExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SetLanguageExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ZCurveExpression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An indexing language script derived from a search definition. An indexing script contains a set of indexing
 * statements, organized in a composite structure of indexing code snippets.
 *
 * @author bratseth
 */
public final class IndexingScript extends Derived implements IlscriptsConfig.Producer {

    private final List<String> docFields = new ArrayList<>();
    private final List<Expression> expressions = new ArrayList<>();
    private List<ImmutableSDField> fieldsSettingLanguage;

    public IndexingScript(Schema schema) {
        derive(schema);
    }

    @Override
    protected void derive(Schema schema) {
        fieldsSettingLanguage = fieldsSettingLanguage(schema);
        if (fieldsSettingLanguage.size() == 1) // Assume this language should be used for all fields
            addExpression(fieldsSettingLanguage.get(0).getIndexingScript());
        super.derive(schema);
    }

    @Override
    protected void derive(ImmutableSDField field, Schema schema) {
        if (field.isImportedField()) return;

        if (field.hasFullIndexingDocprocRights())
            docFields.add(field.getName());

        if (field.usesStructOrMap() && ! GeoPos.isAnyPos(field)) {
            return; // unsupported
        }

        if (fieldsSettingLanguage.size() == 1 && fieldsSettingLanguage.get(0).equals(field))
            return; // Already added

        addExpression(field.getIndexingScript());
    }

    private void addExpression(ScriptExpression expression) {
        if ( expression.isEmpty()) return;
        expressions.add(new StatementExpression(new ClearStateExpression(), new GuardExpression(expression)));
    }

    private List<ImmutableSDField> fieldsSettingLanguage(Schema schema) {
        return schema.allFieldsList().stream()
                     .filter(field -> ! field.isImportedField())
                     .filter(field -> field.containsExpression(SetLanguageExpression.class))
                     .toList();
    }

    public Iterable<Expression> expressions() {
        return Collections.unmodifiableCollection(expressions);
    }

    @Override
    public String getDerivedName() {
        return "ilscripts";
    }

    @Override
    public void getConfig(IlscriptsConfig.Builder configBuilder) {
        IlscriptsConfig.Ilscript.Builder ilscriptBuilder = new IlscriptsConfig.Ilscript.Builder();
        ilscriptBuilder.doctype(getName());
        ilscriptBuilder.docfield(docFields);
        addContentInOrder(ilscriptBuilder);
        configBuilder.ilscript(ilscriptBuilder);
    }

    private void addContentInOrder(IlscriptsConfig.Ilscript.Builder ilscriptBuilder) {
        ArrayList<Expression> later = new ArrayList<>();
        Set<String> touchedFields = new HashSet<>();
        for (Expression expression : expressions) {
            if (modifiesSelf(expression) && ! setsLanguage(expression))
                later.add(expression);
            else
                ilscriptBuilder.content(expression.toString());

            FieldScanVisitor fieldFetcher = new FieldScanVisitor();
            fieldFetcher.visit(expression);
            touchedFields.addAll(fieldFetcher.touchedFields());
        }
        for (Expression exp : later)
            ilscriptBuilder.content(exp.toString());
        generateSyntheticStatementsForUntouchedFields(ilscriptBuilder, touchedFields);
    }

    private void generateSyntheticStatementsForUntouchedFields(Builder ilscriptBuilder, Set<String> touchedFields) {
        Set<String> fieldsWithSyntheticStatements = new HashSet<>(docFields);
        fieldsWithSyntheticStatements.removeAll(touchedFields);
        List<String> orderedFields = new ArrayList<>(fieldsWithSyntheticStatements);
        Collections.sort(orderedFields);
        for (String fieldName : orderedFields) {
            StatementExpression copyField = new StatementExpression(new InputExpression(fieldName),
                    new PassthroughExpression(fieldName));
            ilscriptBuilder.content(copyField.toString());
        }
    }

    private boolean setsLanguage(Expression expression) {
        SetsLanguageVisitor visitor = new SetsLanguageVisitor();
        visitor.visit(expression);
        return visitor.setsLanguage;
    }

    private boolean modifiesSelf(Expression expression) {
        ModifiesSelfVisitor visitor = new ModifiesSelfVisitor();
        visitor.visit(expression);
        return visitor.modifiesSelf();
    }

    private static class ModifiesSelfVisitor extends ExpressionVisitor {

        private String inputField = null;
        private String outputField = null;

        public boolean modifiesSelf() { return outputField != null && outputField.equals(inputField); }

        @Override
        protected void doVisit(Expression expression) {
            if (modifiesSelf()) return;

            if (expression instanceof InputExpression) {
                inputField = ((InputExpression) expression).getFieldName();
            }
            if (expression instanceof OutputExpression) {
                outputField = ((OutputExpression) expression).getFieldName();
            }
        }
    }

    private static class SetsLanguageVisitor extends ExpressionVisitor {

        boolean setsLanguage = false;

        @Override
        protected void doVisit(Expression expression) {
            if (expression instanceof SetLanguageExpression)
                setsLanguage = true;
        }

    }

    private static class FieldScanVisitor extends ExpressionVisitor {
        List<String> touchedFields = new ArrayList<String>();
        List<String> candidates = new ArrayList<String>();

        @Override
        protected void doVisit(Expression exp) {
            if (exp instanceof OutputExpression) {
                touchedFields.add(((OutputExpression) exp).getFieldName());
            }
            if (exp instanceof InputExpression) {
                candidates.add(((InputExpression) exp).getFieldName());
            }
            if (exp instanceof ZCurveExpression) {
                touchedFields.addAll(candidates);
            }
        }

        Collection<String> touchedFields() {
            Collection<String> output = touchedFields;
            touchedFields = null; // deny re-use to try and avoid obvious bugs
            return output;
        }
    }
}
