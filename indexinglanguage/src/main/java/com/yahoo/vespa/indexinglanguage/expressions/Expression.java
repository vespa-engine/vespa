// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.*;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import com.yahoo.vespa.objects.Selectable;

import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public abstract class Expression extends Selectable {

    private final DataType requiredInputType;

    // Input and output types resolved during verification
    private DataType inputType;
    private DataType outputType;

    /**
     * Creates an expression
     *
     * @param requiredInputType the type of the input this expression can work with.
     *                          UnresolvedDataType.INSTANCE if it works with any type,
     *                          and null if it does not consume any input.
     */
    protected Expression(DataType requiredInputType) {
        this.requiredInputType = requiredInputType;
    }

    /**
     * Returns an expression where the children of this has been converted using the given converter.
     * This default implementation returns this as it has no children.
     */
    public Expression convertChildren(ExpressionConverter converter) { return this; }

    /** Sets the document type and field the statement this expression is part of will write to */
    public void setStatementOutput(DocumentType documentType, Field field) {}

    public final DataType requiredInputType() { return requiredInputType; }

    public DataType getInputType(VerificationContext context) { return inputType; }

    /**
     * Sets the input type of this and returns the resulting output type, or null if it cannot be
     * uniquely determined.
     * This default implementation returns the same type, which is appropriate for all statements
     * that do not change the type.
     *
     * @param inputType the type to set as the input type of this, or null if it cannot be determined
     * @param requiredType the type the input type must be assignable to
     * @param context the context of this
     * @throws IllegalArgumentException if inputType isn't assignable to requiredType
     */
    protected final DataType setInputType(DataType inputType, DataType requiredType, VerificationContext context) {
        // TODO: Activate type checking
        // if ( ! (inputType instanceof TensorDataType))
        //    throw new IllegalArgumentException(this + " requires a " + requiredType + ", but gets " + inputType);
        this.inputType = inputType;
        return inputType;
    }

    /**
     * Sets the input type of this and returns the resulting output type, or null if it cannot be
     * uniquely determined.
     * Subtypes may implement this by calling setInputType(inputType, requiredType, VerificationContext context).
     */
    public DataType setInputType(DataType inputType, VerificationContext context) {
        this.inputType = inputType;
        return inputType;
    }

    /**
     * Returns the output type this is must produce (since it is part of a statement expression),
     * or null if this is not set or there is no output produced at the end of the statement.
     */
    public DataType getOutputType(VerificationContext context) { return outputType; }

    /**
     * Sets the output type of this and returns the resulting input type, or null if it cannot be
     * uniquely determined.
     * This implementation returns the same type, which is appropriate for all statements
     * that do not change the type.
     *
     * @param outputType the type to set as the output type of this, or null if it cannot be determined
     * @param requiredType the type the output type must be assignable to
     * @param context the context of this
     * @throws IllegalArgumentException if outputType isn't assignable to requiredType
     */
    protected final DataType setOutputType(DataType outputType, DataType requiredType, VerificationContext context) {
        // TODO: Activate type checking
        // if (outputType != null && ! requiredType.isAssignableFrom(outputType))
        //     throw new IllegalArgumentException(this + " produces a " + outputType + " but " + requiredType + " is required");
        this.outputType = outputType;
        return outputType;
    }

    /**
     * Sets the output type of this and returns the resulting input type, or null if it cannot be
     * uniquely determined.
     * Subtypes implement this by calling setOutputType(outputType, requiredType, VerificationContext context).
     */
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        this.outputType = outputType;
        return outputType;
    }

    public abstract DataType createdOutputType();

    /** Implementations that don't change the type should implement this to do verification. */
    protected void doVerify(VerificationContext context) {}

    public final DataType verify() {
        return verify(new VerificationContext());
    }

    public final DataType verify(DataType val) {
        return verify(new VerificationContext().setCurrentType(val));
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
        if (requiredInputType != null) {
            DataType input = context.getCurrentType();
            if (input == null) {
                throw new VerificationException(this, "Expected " + requiredInputType.getName() + " input, but no input is specified");
            }
            if (input.getPrimitiveType() == UnresolvedDataType.INSTANCE) {
                throw new VerificationException(this, "Failed to resolve input type");
            }
            if (!requiredInputType.isAssignableFrom(input)) {
                throw new VerificationException(this, "Expected " + requiredInputType.getName() + " input, got " +
                                                      input.getName());
            }
        }
        doVerify(context);
        DataType outputType = createdOutputType();
        if (outputType != null) {
            DataType output = context.getCurrentType();
            if (output == null) {
                throw new VerificationException(this, "Expected " + outputType.getName() + " output, but no output is specified");
            }
            if (output.getPrimitiveType() == UnresolvedDataType.INSTANCE) {
                throw new VerificationException(this, "Failed to resolve output type");
            }
            if (!outputType.isAssignableFrom(output)) {
                throw new VerificationException(this, "Expected " + outputType.getName() + " output, got " + output.getName());
            }
        }
        return context.getCurrentType();
    }

    public final FieldValue execute(FieldValue val) {
        return execute(new ExecutionContext().setCurrentValue(val));
    }

    public final Document execute(AdapterFactory factory, Document doc) {
        return execute(factory.newDocumentAdapter(doc));
    }

    public final Document execute(DocumentAdapter adapter) {
        execute((FieldValueAdapter)adapter);
        return adapter.getFullOutput();
    }

    public static DocumentUpdate execute(Expression expression, AdapterFactory factory, DocumentUpdate update) {
        DocumentUpdate result = null;
        for (UpdateAdapter adapter : factory.newUpdateAdapterList(update)) {
            DocumentUpdate output = adapter.getExpression(expression).execute(adapter);
            if (output == null) {
                // ignore
            } else if (result != null) {
                result.addAll(output);
            } else {
                result = output;
            }
        }
        if (result != null) {
            result.setCreateIfNonExistent(update.getCreateIfNonExistent());
        }
        return result;
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
            FieldValue input = context.getCurrentValue();
            if (input == null) return null;

            if (!inputType.isValueCompatible(input)) {
                throw new IllegalArgumentException("Expression '" + this + "' expected " + inputType.getName() +
                                                   " input, got " + input.getDataType().getName());
            }
        }
        doExecute(context);
        DataType outputType = createdOutputType();
        if (outputType != null) {
            FieldValue output = context.getCurrentValue();
            if (output != null && !outputType.isValueCompatible(output)) {
                throw new IllegalStateException("Expression '" + this + "' expected " + outputType.getName() +
                                                " output, got " + output.getDataType().getName());
            }
        }
        return context.getCurrentValue();
    }

    protected abstract void doExecute(ExecutionContext context);

    /** Creates an expression with simple lingustics for testing */
    public static Expression fromString(String expression) throws ParseException {
        return fromString(expression, new SimpleLinguistics(), Embedder.throwsOnUse.asMap());
    }

    public static Expression fromString(String expression, Linguistics linguistics, Map<String, Embedder> embedders) throws ParseException {
        return newInstance(new ScriptParserContext(linguistics, embedders).setInputStream(new IndexingInput(expression)));
    }

    public static Expression newInstance(ScriptParserContext context) throws ParseException {
        return ScriptParser.parseExpression(context);
    }

    protected static boolean equals(Object lhs, Object rhs) {
        if (lhs == null) {
            return rhs == null;
        } else {
            if (rhs == null) {
                return false;
            }
            return lhs.equals(rhs);
        }
    }

    // Convenience For testing
    public static Document execute(Expression expression, Document doc) {
        return expression.execute(new SimpleAdapterFactory(), doc);
    }

    public static DocumentUpdate execute(Expression expression, DocumentUpdate update) {
        return execute(expression, new SimpleAdapterFactory(), update);
    }

    public final FieldValue execute() {
        return execute(new ExecutionContext());
    }

}
