// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.objects.ObjectOperation;
import com.yahoo.vespa.objects.ObjectPredicate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public final class SwitchExpression extends CompositeExpression {

    private final Map<String, Expression> cases = new LinkedHashMap<>();
    private final Expression defaultExp;

    public <T extends Expression> SwitchExpression(Map<String, T> cases) {
        this(cases, null);
    }

    public <T extends Expression> SwitchExpression(Map<String, T> cases, Expression defaultExp) {
        super(null);
        this.defaultExp = defaultExp;
        this.cases.putAll(cases);
    }

    public boolean isEmpty() {
        return defaultExp == null && cases.isEmpty();
    }

    public Map<String, Expression> getCases() {
        return Collections.unmodifiableMap(cases);
    }

    public Expression getDefaultExpression() {
        return defaultExp;
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        defaultExp.setStatementOutput(documentType, field);
        for (var expression : cases.values())
            expression.setStatementOutput(documentType, field);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue input = context.getValue();
        Expression exp = null;
        if (input != null) {
            if (!(input instanceof StringFieldValue)) {
                throw new IllegalArgumentException("Expected " + DataType.STRING.getName() + " input, got " +
                                                   input.getDataType().getName() + ".");
            }
            exp = cases.get(String.valueOf(input));
        }
        if (exp == null) {
            exp = defaultExp;
        }
        if (exp != null) {
            exp.execute(context);
        }
        context.setValue(input);
    }

    @Override
    public void selectMembers(ObjectPredicate predicate, ObjectOperation operation) {
        select(defaultExp, predicate, operation);
        for (Expression exp : cases.values()) {
            select(exp, predicate, operation);
        }
    }

    @Override
    protected void doVerify(VerificationContext context) {
        DataType input = context.getValueType();
        if (input == null) {
            throw new VerificationException(this, "Expected " + DataType.STRING.getName() + " input, got null.");
        }
        if (input != DataType.STRING) {
            throw new VerificationException(this, "Expected " + DataType.STRING.getName() + " input, got " +
                                                  input.getName() + ".");
        }
        for (Expression exp : cases.values()) {
            context.setValueType(input).execute(exp);
        }
        context.setValueType(input).execute(defaultExp);
        context.setValueType(input);
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("switch { ");
        for (Map.Entry<String, Expression> entry : cases.entrySet()) {
            ret.append("case \"").append(StringUtilities.escape(entry.getKey(), '"')).append("\": ");
            Expression exp = entry.getValue();
            ret.append(exp).append("; ");
        }
        if (defaultExp != null) {
            ret.append("default: ").append(defaultExp).append("; ");
        }
        ret.append("}");
        return ret.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SwitchExpression rhs)) return false;
        if (!cases.equals(rhs.cases)) return false;
        if (!equals(defaultExp, rhs.defaultExp)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + (defaultExp != null ? defaultExp.hashCode() : 0);
    }

}
