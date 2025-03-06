// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.TextGenerator;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.AdapterFactory;
import com.yahoo.vespa.indexinglanguage.DocumentAdapter;
import com.yahoo.vespa.indexinglanguage.DocumentTypeAdapter;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.ScriptParser;
import com.yahoo.vespa.indexinglanguage.ScriptParserContext;
import com.yahoo.vespa.indexinglanguage.SimpleAdapterFactory;
import com.yahoo.vespa.indexinglanguage.UpdateAdapter;
import com.yahoo.vespa.indexinglanguage.parser.IndexingInput;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import com.yahoo.vespa.objects.Selectable;

import java.util.Map;

/**
 * Superclass of expressions.
 *
 * Rules:
 * - All expressions produce an output.
 * - Expressions that does not require an input overrides requiresInput() to return false
 *
 * @author Simon Thoresen Hult
 * @author bratseth
 */
public abstract class Expression extends Selectable {

    // Input and output types resolved during verification
    private DataType inputType;
    private DataType outputType;

    /** Returns whether this expression requires an input value. */
    public boolean requiresInput() { return true; }

    /**
     * Returns whether this expression outputs a different value than what it gets as input.
     * Annotating a string value does not count as modifying it.
     */
    public boolean isMutating() { return true; }

    /**
     * Returns an expression where the children of this has been converted using the given converter.
     * This default implementation returns this as it has no children.
     */
    public Expression convertChildren(ExpressionConverter converter) { return this; }

    /** Sets the document type and field the statement this expression is part of will write to */
    public void setStatementOutput(DocumentType documentType, Field field) {}

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
     * @return input the input type of this expression resolved from this call and current state
     * @throws IllegalArgumentException if inputType isn't assignable to requiredType
     */
    protected final DataType setInputType(DataType inputType, DataType requiredType, VerificationContext context) {
        if (requiredType != null && inputType == null)
            throw new VerificationException(this, "Expected " + requiredType.getName() + " input, but no input is provided");
        if (requiredType != null && ! (inputType.isAssignableTo(requiredType)))
            throw new VerificationException(this, "Expected " + requiredType.getName() + " input, got " + inputType.getName());
        return assignInputType(inputType);
    }

    /**
     * Sets the input type of this and returns the resulting output type, or null if it cannot be
     * uniquely determined.
     * Subtypes may implement this by calling setInputType(inputType, requiredType, VerificationContext context).
     */
    public DataType setInputType(DataType inputType, VerificationContext context) {
        return assignInputType(inputType);
    }

    DataType oldassignInputType(DataType inputType) {
        // Since we assign in both directions, in both orders, we may already know
        if (this.inputType == null)
            this.inputType = inputType;
        return this.inputType;
    }

    DataType assignInputType(DataType inputType) {
        // Since we assign in both directions, we may already have more precise info
        return this.inputType = leastGeneralNonNullOf(this.inputType, inputType);
    }

    /**
     * Returns the output type this is must produce (since it is part of a statement expression),
     * or null if this is not set or there is no output produced at the end of the statement.
     */
    public DataType getOutputType(VerificationContext context) { return outputType; }

    /** Returns the already assigned (during verification) output type, or null if no type is assigned. */
    public DataType getOutputType() { return outputType; }

    /** Returns the already assigned (during verification) output type, or throws an exception if no type is assigned. */
    public DataType requireOutputType() {
        if (outputType == null)
            throw new IllegalStateException("The output type of " + this + " is unresolved");
        return outputType;
    }

    /**
     * Sets the output type of this and returns the resulting input type, or null if it cannot be
     * uniquely determined, with additional arguments for convenience type checking.
     * This implementation returns the same type, which is appropriate for all statements
     * that do not change the type.
     *
     * @param actualOutput the type actually produced by this, must be assignable to the requiredOutput,
     *                     or null if not known
     * @param requiredOutput the type required by the next expression, which actualOutput must be assignable to,
     *                       or null if it cannot be uniquely determined.
     * @param requiredType a type the required output must be assignable to, or null to not verify this
     * @param context the context of this
     * @return the actualOutput if set, requiredOutput otherwise
     * @throws IllegalArgumentException if actualOutput
     */
    protected final DataType setOutputType(DataType actualOutput, DataType requiredOutput, DataType requiredType,
                                           VerificationContext context) {
        if (actualOutput != null && requiredOutput != null && ! actualOutput.isAssignableTo(requiredOutput))
            throw new VerificationException(this, "This produces type " + actualOutput.getName() + " but " + requiredOutput.getName() + " is required");
        if (requiredType != null && requiredOutput != null && ! requiredOutput.isAssignableTo(requiredType))
            throw new VerificationException(this, "This is required to produce type " + requiredOutput.getName() + " but is produces " + requiredType.getName());;
        return assignOutputType(actualOutput != null ? actualOutput : requiredOutput); // Use the more precise type when known
    }

    /**
     * Sets the output type of this and returns the resulting input type, or null if it cannot be
     * uniquely determined.
     * Subtypes implement this by calling setOutputType(outputType, requiredType, VerificationContext context).
     */
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        return assignOutputType(outputType);
    }

    DataType assignOutputType(DataType outputType) {
        // Since we assign in both directions, we may already have more precise info
        return this.outputType = leastGeneralNonNullOf(this.outputType, outputType);
    }

    public abstract DataType createdOutputType();

    public final void verify(DocumentType type) {
        verify(new DocumentTypeAdapter(type));
    }

    public final void verify(Document doc) {
        verify(new SimpleAdapterFactory(), doc);
    }

    public final void verify(AdapterFactory factory, Document doc) {
        verify(factory.newDocumentAdapter(doc));
    }

    public final void verify(DocumentAdapter adapter) {
        verify((FieldTypeAdapter)adapter);
        adapter.getFullOutput();
    }

    public final void verify(DocumentUpdate upd) {
        verify(new SimpleAdapterFactory(), upd);
    }

    public final void verify(AdapterFactory factory, DocumentUpdate upd) {
        for (UpdateAdapter adapter : factory.newUpdateAdapterList(upd))
            verify(adapter);
    }

    public final void verify(UpdateAdapter adapter) {
        verify((FieldTypeAdapter)adapter);
    }

    public final void verify(FieldTypeAdapter adapter) {
        verify(new VerificationContext(adapter));
    }

    public final void verify(VerificationContext context) {
        doVerify(context);
    }

    protected void doVerify(VerificationContext context) {}

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
        if (requiresInput()) {
            FieldValue input = context.getCurrentValue();
            if (input == null) return null;
        }
        doExecute(context);
        return context.getCurrentValue();
    }

    protected abstract void doExecute(ExecutionContext context);

    /** Creates an expression with simple lingustics for testing */
    public static Expression fromString(String expression) throws ParseException {
        return fromString(expression, new SimpleLinguistics(), Embedder.throwsOnUse.asMap());
    }

    public static Expression fromString(String expression, Linguistics linguistics, Map<String, Embedder> embedders) throws ParseException {
        return newInstance(new ScriptParserContext(linguistics, embedders, Map.of()).setInputStream(new IndexingInput(expression)));
    }
    
    public static Expression fromString(String expression, Linguistics linguistics, Map<String, Embedder> embedders, Map<String, TextGenerator> generators) throws ParseException {
        return newInstance(new ScriptParserContext(linguistics, embedders, generators).setInputStream(new IndexingInput(expression)));
    }

    public static Expression newInstance(ScriptParserContext context) throws ParseException {
        return ScriptParser.parseExpression(context);
    }

    // Convenience For testing
    public static Document execute(Expression expression, Document doc) {
        expression.verify(doc);
        return expression.execute(new SimpleAdapterFactory(), doc);
    }

    public static DocumentUpdate execute(Expression expression, DocumentUpdate update) {
        return execute(expression, new SimpleAdapterFactory(), update);
    }

    public final FieldValue execute() {
        return execute(new ExecutionContext());
    }

    protected DataType mostGeneralOf(DataType left, DataType right) {
        if (left == null || right == null) return null;
        if (left.isAssignableTo(right)) return right;
        if (right.isAssignableTo(left)) return left;
        throw new VerificationException(this, left.getName() + " is incompatible with " + right.getName());
    }

    protected DataType leastGeneralOf(DataType left, DataType right) {
        if (left == null || right == null) return null;
        if (left.isAssignableTo(right)) return left;
        if (right.isAssignableTo(left)) return right;
        throw new VerificationException(this, left.getName() + " is incompatible with " + right.getName());
    }

    protected DataType mostGeneralNonNullOf(DataType left, DataType right) {
        if (left == null) return right;
        if (right == null) return left;
        if (left.isAssignableTo(right)) return right;
        if (right.isAssignableTo(left)) return left;
        throw new VerificationException(this, left.getName() + " is incompatible with " + right.getName());
    }

    protected DataType leastGeneralNonNullOf(DataType left, DataType right) {
        if (left == null) return right;
        if (right == null) return left;
        if (left.isAssignableTo(right)) return left;
        if (right.isAssignableTo(left)) return right;
        throw new VerificationException(this, left.getName() + " is incompatible with " + right.getName());
    }

}
