// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.api.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.EvaluationContext;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.functions.PrimitiveTensorFunction;
import com.yahoo.tensor.functions.ScalarFunction;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.tensor.functions.ToStringContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A node which performs a tensor function
 *
 * @author bratseth
 */
@Beta
public class TensorFunctionNode extends CompositeNode {

    private final TensorFunction<Reference> function;

    public TensorFunctionNode(TensorFunction<Reference> function) {
        this.function = function;
    }

    /** Returns the tensor function wrapped by this */
    public TensorFunction<Reference> function() { return function; }

    @Override
    public List<ExpressionNode> children() {
        return function.arguments().stream()
                                           .map(this::toExpressionNode)
                                           .collect(Collectors.toList());
    }

    private ExpressionNode toExpressionNode(TensorFunction<Reference> f) {
        if (f instanceof ExpressionTensorFunction)
            return ((ExpressionTensorFunction)f).expression;
        else
            return new TensorFunctionNode(f);
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> children) {
        List<TensorFunction<Reference>> wrappedChildren = children.stream()
                                                                 .map(ExpressionTensorFunction::new)
                                                                 .collect(Collectors.toList());
        return new TensorFunctionNode(function.withArguments(wrappedChildren));
    }

    @Override
    public StringBuilder toString(StringBuilder string, SerializationContext context, Deque<String> path, CompositeNode parent) {
        // Serialize as primitive
        return string.append(function.toPrimitive().toString(new ExpressionToStringContext(context, path, this)));
    }

    @Override
    public TensorType type(TypeContext<Reference> context) {
        return function.type(context);
    }

    @Override
    public Value evaluate(Context context) {
        return new TensorValue(function.evaluate(context));
    }

    public static ExpressionTensorFunction wrap(ExpressionNode node) {
        return new ExpressionTensorFunction(node);
    }

    public static Map<TensorAddress, ScalarFunction<Reference>> wrapScalars(Map<TensorAddress, ExpressionNode> nodes) {
        Map<TensorAddress, ScalarFunction<Reference>> functions = new LinkedHashMap<>();
        for (var entry : nodes.entrySet())
            functions.put(entry.getKey(), wrapScalar(entry.getValue()));
        return functions;
    }

    public static void wrapScalarBlock(TensorType type,
                                       List<String> dimensionOrder,
                                       String mappedDimensionLabel,
                                       List<ExpressionNode> nodes,
                                       Map<TensorAddress, ScalarFunction<Reference>> receivingMap) {
        TensorType denseSubtype = new TensorType(type.valueType(),
                                                 type.dimensions().stream().filter(d -> d.isIndexed()).collect(Collectors.toList()));
        List<String> denseDimensionOrder = new ArrayList<>(dimensionOrder);
        denseDimensionOrder.retainAll(denseSubtype.dimensionNames());
        IndexedTensor.Indexes indexes = IndexedTensor.Indexes.of(denseSubtype, denseDimensionOrder);
        if (indexes.size() != nodes.size())
            throw new IllegalArgumentException("At '" + mappedDimensionLabel + "': Need " + indexes.size() +
                                               " values to fill a dense subspace of " + type + " but got " + nodes.size());
        for (ExpressionNode node : nodes) {
            indexes.next();

            // Insert the mapped dimension into the dense subspace address of indexes
            String[] labels = new String[type.rank()];
            int indexedDimensionsIndex = 0;
            int allDimensionsIndex = 0;
            for (TensorType.Dimension dimension : type.dimensions()) {
                if (dimension.isIndexed())
                    labels[allDimensionsIndex++] = String.valueOf(indexes.indexesForReading()[indexedDimensionsIndex++]);
                else
                    labels[allDimensionsIndex++] = mappedDimensionLabel;
            }

            receivingMap.put(TensorAddress.of(labels), wrapScalar(node));
        }
    }

    public static List<ScalarFunction<Reference>> wrapScalars(TensorType type,
                                                              List<String> dimensionOrder,
                                                              List<ExpressionNode> nodes) {
        IndexedTensor.Indexes indexes = IndexedTensor.Indexes.of(type, dimensionOrder);
        if (indexes.size() != nodes.size())
            throw new IllegalArgumentException("Need " + indexes.size() + " values to fill " + type + " but got " + nodes.size());

        List<ScalarFunction<Reference>> wrapped = new ArrayList<>(nodes.size());
        while (indexes.hasNext()) {
            indexes.next();
            wrapped.add(wrapScalar(nodes.get((int)indexes.toSourceValueIndex())));
        }
        return wrapped;
    }

    public static ScalarFunction<Reference> wrapScalar(ExpressionNode node) {
        return new ExpressionScalarFunction(node);
    }

    @Override
    public int hashCode() { return function.hashCode(); }

    private static class ExpressionScalarFunction implements ScalarFunction<Reference> {

        private final ExpressionNode expression;

        public ExpressionScalarFunction(ExpressionNode expression) {
            this.expression = expression;
        }

        @Override
        public Double apply(EvaluationContext<Reference> context) {
            return expression.evaluate(new ContextWrapper(context)).asDouble();
        }

        @Override
        public Optional<TensorFunction<Reference>> asTensorFunction() {
            return Optional.of(new ExpressionTensorFunction(expression));
        }

        @Override
        public String toString() {
            return toString(ExpressionToStringContext.empty);
        }

        @Override
        public String toString(ToStringContext<Reference> c) {
            ToStringContext<Reference> outermost = c;
            while (outermost.parent() != null)
                outermost = outermost.parent();

            if (outermost instanceof ExpressionToStringContext) {
                ExpressionToStringContext context = (ExpressionToStringContext)outermost;
                ExpressionNode root = expression;
                if (root instanceof CompositeNode && ! (root instanceof EmbracedNode) && ! isIdentifierReference(root))
                    root = new EmbracedNode(root); // Output embraced if composite
                return root.toString(new StringBuilder(),
                                     new ExpressionToStringContext(context.wrappedSerializationContext, c, context.path, context.parent),
                                     context.path,
                                     context.parent).toString();
            }
            else {
                return expression.toString();
            }
        }

        private boolean isIdentifierReference(ExpressionNode node) {
            if ( ! (node instanceof ReferenceNode)) return false;
            return ((ReferenceNode)node).reference().isIdentifier();
        }

    }

    /**
     * A tensor function implemented by an expression.
     * This allows us to pass expressions as tensor function arguments.
     */
    public static class ExpressionTensorFunction extends PrimitiveTensorFunction<Reference> {

        /** An expression which produces a tensor */
        private final ExpressionNode expression;

        public ExpressionTensorFunction(ExpressionNode expression) {
            this.expression = expression;
        }

        @Override
        public List<TensorFunction<Reference>> arguments() {
            if (expression instanceof CompositeNode)
                return ((CompositeNode)expression).children().stream()
                                                             .map(ExpressionTensorFunction::new)
                                                             .collect(Collectors.toList());
            else
                return Collections.emptyList();
        }

        @Override
        public TensorFunction<Reference> withArguments(List<TensorFunction<Reference>> arguments) {
            if (arguments.size() == 0) return this;
            List<ExpressionNode> unwrappedChildren = arguments.stream()
                                                              .map(arg -> ((ExpressionTensorFunction)arg).expression)
                                                              .collect(Collectors.toList());
            return new ExpressionTensorFunction(((CompositeNode)expression).setChildren(unwrappedChildren));
        }

        @Override
        public PrimitiveTensorFunction<Reference> toPrimitive() { return this; }

        @Override
        public TensorType type(TypeContext<Reference> context) {
            return expression.type(context);
        }

        @Override
        public Optional<ScalarFunction<Reference>> asScalarFunction() {
            return Optional.of(new ExpressionScalarFunction(expression));
        }

        @Override
        public Tensor evaluate(EvaluationContext<Reference> context) {
            return expression.evaluate((Context)context).asTensor();
        }

        @Override
        public String toString() {
            return toString(ExpressionToStringContext.empty);
        }

        @Override
        public int hashCode() { return expression.hashCode(); }

        @Override
        public String toString(ToStringContext<Reference> c) {
            ToStringContext<Reference> outermost = c;
            while (outermost.parent() != null)
                outermost = outermost.parent();

            if (outermost instanceof ExpressionToStringContext) {
                ExpressionToStringContext context = (ExpressionToStringContext)outermost;
                return expression.toString(new StringBuilder(),
                                           new ExpressionToStringContext(context.wrappedSerializationContext, c,
                                                                         context.path,
                                                                         context.parent),
                                           context.path,
                                           context.parent)
                                                   .toString();
            }
            else {
                return expression.toString();
            }
        }

    }

    /**
     * This is used to pass a full expression serialization context through tensor functions.
     * Tensor functions cannot see the full serialization context because it exposes expressions
     * (which depends on the tensor module), but they need to be able to recursively add their own
     * contexts (binding scopes) due to Generate binding dimension names.
     *
     * To be able to achieve both passing the serialization context through functions *and* allow them
     * to add more context, we need to keep track of both these contexts here separately and map between
     * contexts as seen in the toString methods of the function classes above.
     */
    private static class ExpressionToStringContext extends SerializationContext implements ToStringContext<Reference> {

        private final ToStringContext<Reference> wrappedToStringContext;
        private final SerializationContext wrappedSerializationContext;
        private final Deque<String> path;
        private final CompositeNode parent;

        public static final ExpressionToStringContext empty = new ExpressionToStringContext(new SerializationContext(),
                                                                                            null,
                                                                                            null);

        ExpressionToStringContext(SerializationContext wrappedSerializationContext, Deque<String> path, CompositeNode parent) {
            this(wrappedSerializationContext, null, path, parent);
        }

        ExpressionToStringContext(SerializationContext wrappedSerializationContext,
                                  ToStringContext<Reference> wrappedToStringContext,
                                  Deque<String> path,
                                  CompositeNode parent) {
            this.wrappedSerializationContext = wrappedSerializationContext;
            this.wrappedToStringContext = wrappedToStringContext;
            this.path = path;
            this.parent = parent;
        }

        /** Adds the serialization of a function */
        public void addFunctionSerialization(String name, String expressionString) {
            wrappedSerializationContext.addFunctionSerialization(name, expressionString);
        }

        /** Adds the serialization of the an argument type to a function */
        public void addArgumentTypeSerialization(String functionName, String argumentName, TensorType type) {
            wrappedSerializationContext.addArgumentTypeSerialization(functionName, argumentName, type);
        }

        /** Adds the serialization of the return type of a function */
        public void addFunctionTypeSerialization(String functionName, TensorType type) {
            wrappedSerializationContext.addFunctionTypeSerialization(functionName, type);
        }

        public Map<String, String> serializedFunctions() {
            return wrappedSerializationContext.serializedFunctions();
        }

        /** Returns a function or null if it isn't defined in this context */
        public ExpressionFunction getFunction(String name) { return wrappedSerializationContext.getFunction(name); }

        /** Returns the type context of this, or empty if none. */
        @Override
        public Optional<TypeContext<Reference>> typeContext() {
            return wrappedSerializationContext.typeContext();
        }

        @Override
        protected Map<String, ExpressionFunction> getFunctions() { return wrappedSerializationContext.getFunctions(); }

        public ToStringContext<Reference> parent() { return wrappedToStringContext; }

        /** Returns the resolution of an identifier, or null if it isn't defined in this context */
        @Override
        public String getBinding(String name) {
            if (wrappedToStringContext != null && wrappedToStringContext.getBinding(name) != null)
                return wrappedToStringContext.getBinding(name);
            else
                return wrappedSerializationContext.getBinding(name);
        }

        /** Returns a new context with the bindings replaced by the given bindings */
        @Override
        public ExpressionToStringContext withBindings(Map<String, String> bindings) {
            SerializationContext serializationContext = new SerializationContext(getFunctions(), bindings, typeContext(), serializedFunctions());
            return new ExpressionToStringContext(serializationContext, wrappedToStringContext, path, parent);
        }

        /** Returns a fresh context without bindings */
        @Override
        public SerializationContext withoutBindings() {
            SerializationContext serializationContext = new SerializationContext(getFunctions(), null, typeContext(), serializedFunctions());
            return new ExpressionToStringContext(serializationContext, null, path, parent);
        }

        @Override
        public String toString() {
            return "TensorFunctionNode.ExpressionToStringContext with wrapped serialization context: " + wrappedSerializationContext;
        }

    }

    /** Turns an EvaluationContext into a Context */
    // TODO: We should be able to change RankingExpression.evaluate to take an EvaluationContext and then get rid of this
    private static class ContextWrapper extends Context {

        private final EvaluationContext<Reference> delegate;

        public ContextWrapper(EvaluationContext<Reference> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Value get(String name) {
            return new TensorValue(delegate.getTensor(name));
        }

        @Override
        public TensorType getType(Reference name) {
            return delegate.getType(name);
        }

    }

}
