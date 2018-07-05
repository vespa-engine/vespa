// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.*;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import com.yahoo.vespa.objects.Selectable;

/**
 * @author Simon Thoresen Hult
 */
public abstract class Expression extends Selectable {

    public final FieldValue execute(FieldValue val) {
        return execute(new ExecutionContext().setValue(val));
    }

    public final Document execute(AdapterFactory factory, Document doc) {
        return execute(factory.newDocumentAdapter(doc));
    }

    public final Document execute(DocumentAdapter adapter) {
        execute((FieldValueAdapter)adapter);
        return adapter.getFullOutput();
    }

    public static DocumentUpdate execute(Expression expression, AdapterFactory factory, DocumentUpdate update) {
        DocumentUpdate ret = null;
        for (UpdateAdapter adapter : factory.newUpdateAdapterList(update)) {
            DocumentUpdate output = adapter.getExpression(expression).execute(adapter);
            if (output == null) {
                // ignore
            } else if (ret != null) {
                ret.addAll(output);
            } else {
                ret = output;
            }
        }
        if (ret != null) {
            ret.setCreateIfNonExistent(update.getCreateIfNonExistent());
        }
        return ret;
    }

    public final DocumentUpdate execute(UpdateAdapter adapter) {
        execute((FieldValueAdapter)adapter);
        return adapter.getOutput();
    }

    public final FieldValue execute(FieldValueAdapter adapter) {
        return execute(new ExecutionContext(adapter));
    }

    public final FieldValue execute(ExecutionContext context) {
        DataType inputType = requiredInputType();
        if (inputType != null) {
            FieldValue input = context.getValue();
            if (input == null) {
                return null;
            }
            if (!inputType.isValueCompatible(input)) {
                throw new IllegalArgumentException("Expression '" + this + "' expected " + inputType.getName() +
                                                   " input, got " + input.getDataType().getName() + ".");
            }
        }
        doExecute(context);
        DataType outputType = createdOutputType();
        if (outputType != null) {
            FieldValue output = context.getValue();
            if (output != null && !outputType.isValueCompatible(output)) {
                throw new IllegalStateException("Expression '" + this + "' expected " + outputType.getName() +
                                                " output, got " + output.getDataType().getName() + ".");
            }
        }
        return context.getValue();
    }

    protected abstract void doExecute(ExecutionContext ctx);

    public final DataType verify() {
        return verify(new VerificationContext());
    }

    public final DataType verify(DataType val) {
        return verify(new VerificationContext().setValue(val));
    }

    public final Document verify(Document doc) {
        return verify(new SimpleAdapterFactory(), doc);
    }

    public final Document verify(AdapterFactory factory, Document doc) {
        return verify(factory.newDocumentAdapter(doc));
    }

    public final Document verify(DocumentAdapter adapter) {
        verify((FieldTypeAdapter)adapter);
        return adapter.getFullOutput();
    }

    public final DocumentUpdate verify(DocumentUpdate upd) {
        return verify(new SimpleAdapterFactory(), upd);
    }

    public final DocumentUpdate verify(AdapterFactory factory, DocumentUpdate upd) {
        DocumentUpdate ret = null;
        for (UpdateAdapter adapter : factory.newUpdateAdapterList(upd)) {
            DocumentUpdate output = verify(adapter);
            if (output == null) {
                // ignore
            } else if (ret != null) {
                ret.addAll(output);
            } else {
                ret = output;
            }
        }
        return ret;
    }

    public final DocumentUpdate verify(UpdateAdapter adapter) {
        verify((FieldTypeAdapter)adapter);
        return adapter.getOutput();
    }

    public final DataType verify(FieldTypeAdapter adapter) {
        return verify(new VerificationContext(adapter));
    }

    public final DataType verify(VerificationContext context) {
        DataType inputType = requiredInputType();
        if (inputType != null) {
            DataType input = context.getValue();
            if (input == null) {
                throw new VerificationException(this, "Expected " + inputType.getName() + " input, got null.");
            }
            if (input.getPrimitiveType() == UnresolvedDataType.INSTANCE) {
                throw new VerificationException(this, "Failed to resolve input type.");
            }
            if (!inputType.isAssignableFrom(input)) {
                throw new VerificationException(this, "Expected " + inputType.getName() + " input, got " +
                                                      input.getName() + ".");
            }
        }
        doVerify(context);
        DataType outputType = createdOutputType();
        if (outputType != null) {
            DataType output = context.getValue();
            if (output == null) {
                throw new VerificationException(this, "Expected " + outputType.getName() + " output, got null.");
            }
            if (output.getPrimitiveType() == UnresolvedDataType.INSTANCE) {
                throw new VerificationException(this, "Failed to resolve output type.");
            }
            if (!outputType.isAssignableFrom(output)) {
                throw new VerificationException(this, "Expected " + outputType.getName() + " output, got " +
                                                      output.getName() + ".");
            }
        }
        return context.getValue();
    }

    protected abstract void doVerify(VerificationContext context);

    public abstract DataType requiredInputType();

    public abstract DataType createdOutputType();

    /** Creates an expression with simple lingustics for testing */
    public static Expression fromString(String expression) throws ParseException {
        return fromString(expression, new SimpleLinguistics());
    }

    public static Expression fromString(String expression, Linguistics linguistics) throws ParseException {
        return newInstance(new ScriptParserContext(linguistics).setInputStream(new IndexingInput(expression)));
    }

    public static Expression newInstance(ScriptParserContext context) throws ParseException {
        return ScriptParser.parseExpression(context);
    }

    protected static boolean equals(Object lhs, Object rhs) {
        if (lhs == null) {
            if (rhs != null) {
                return false;
            }
        } else {
            if (rhs == null) {
                return false;
            }
            if (!lhs.equals(rhs)) {
                return false;
            }
        }
        return true;
    }

    // Convenience For testing
    public static Document execute(Expression expression, Document doc) {
        return expression.execute(new SimpleAdapterFactory(), doc);
    }
    public static final DocumentUpdate execute(Expression expression, DocumentUpdate update) {
        return execute(expression, new SimpleAdapterFactory(), update);
    }
    public final FieldValue execute() {
        return execute(new ExecutionContext());
    }

}
