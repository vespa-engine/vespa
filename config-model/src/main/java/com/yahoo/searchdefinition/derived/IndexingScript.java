// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.*;
import com.yahoo.searchdefinition.*;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.expressions.*;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig.Ilscript.Builder;

import java.util.*;

/**
 * An indexing language script derived from a search definition. An indexing script contains a set of indexing
 * statements, organized in a composite structure of indexing code snippets.
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon S Bratseth</a>
 */
public final class IndexingScript extends Derived implements IlscriptsConfig.Producer {

    private final List<String> docFields = new LinkedList<>();
    private final List<Expression> expressions = new LinkedList<>();

    public IndexingScript(Search search) {
        derive(search);
    }

    protected void derive(SDField field, Search search) {
        if (!field.isExtraField()) {
            docFields.add(field.getName());
        }
        if (field.usesStructOrMap() &&
            !field.getDataType().equals(PositionDataType.INSTANCE) &&
            !field.getDataType().equals(DataType.getArray(PositionDataType.INSTANCE)))
        {
            return; // unsupported
        }
        ScriptExpression script = field.getIndexingScript();
        if (!script.isEmpty()) {
            expressions.add(new StatementExpression(new ClearStateExpression(),
                                                    new GuardExpression(script)));
        }
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
        for (String fieldName : docFields) {
            ilscriptBuilder.docfield(fieldName);
        }
        addContentInOrder(ilscriptBuilder);
        configBuilder.ilscript(ilscriptBuilder);
    }

    private void addContentInOrder(IlscriptsConfig.Ilscript.Builder ilscriptBuilder) {
        ArrayList<Expression> later = new ArrayList<>();
        Set<String> touchedFields = new HashSet<String>();
        for (Expression exp : expressions) {
            FieldScanVisitor fieldFetcher = new FieldScanVisitor();
            if (modifiesSelf(exp)) {
                later.add(exp);
            } else {
              ilscriptBuilder.content(exp.toString());
            }
            fieldFetcher.visit(exp);
            touchedFields.addAll(fieldFetcher.touchedFields());
        }
        for (Expression exp : later) {
            ilscriptBuilder.content(exp.toString());
        }
        generateSyntheticStatementsForUntouchedFields(ilscriptBuilder, touchedFields);
    }

    private void generateSyntheticStatementsForUntouchedFields(Builder ilscriptBuilder, Set<String> touchedFields) {
        Set<String> fieldsWithSyntheticStatements = new HashSet<String>(docFields);
        fieldsWithSyntheticStatements.removeAll(touchedFields);
        List<String> orderedFields = new ArrayList<String>(fieldsWithSyntheticStatements);
        Collections.sort(orderedFields);
        for (String fieldName : orderedFields) {
            StatementExpression copyField = new StatementExpression(new InputExpression(fieldName),
                    new PassthroughExpression(fieldName));
            ilscriptBuilder.content(copyField.toString());
        }
    }

    private boolean modifiesSelf(Expression exp) {
        MyExpVisitor visitor = new MyExpVisitor();
        visitor.visit(exp);
        return visitor.modifiesSelf();
    }

    private class MyExpVisitor extends ExpressionVisitor {
        private String inputField = null;
        private String outputField = null;

        public boolean modifiesSelf() { return outputField != null && outputField.equals(inputField); }

        @Override
        protected void doVisit(Expression expression) {
            if (modifiesSelf()) {
                return;
            }
            if (expression instanceof InputExpression) {
                inputField = ((InputExpression) expression).getFieldName();
            }
            if (expression instanceof OutputExpression) {
                outputField = ((OutputExpression) expression).getFieldName();
            }
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
