// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.concurrent.DynamicBatcher;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.InvocationContext;
import com.yahoo.metrics.simple.Counter;
import com.yahoo.metrics.simple.Gauge;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.metrics.simple.Point;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.MixedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Embeds a string in a tensor space using the configured Embedder component
 *
 * @author bratseth
 */
public class EmbedExpression extends Expression  {

    private final Linguistics linguistics;
    private final Components.Selected<Embedder> embedder;
    private final String requestedEmbedderId;

    private final DynamicBatcher<BatchKey, EmbedInput, Tensor> batcher;
    private final Gauge batchSize;
    private final Gauge batchQueueTime;
    private final Counter batchCount;

    /** The destination the embedding will be written to on the form [schema name].[field name] */
    private String destination;

    public EmbedExpression(Linguistics linguistics, Components<Embedder> embedders, String embedderId,
                           List<String> arguments) {
        this.linguistics = linguistics;
        this.requestedEmbedderId = embedderId;
        embedder = new Components.Selected<>("embedder", embedders, embedderId, true, arguments);
        this.batcher = null;
        this.batchSize = null;
        this.batchQueueTime = null;
        this.batchCount = null;
    }

    public EmbedExpression(Linguistics linguistics, Components<Embedder> embedders, String embedderId,
                           List<String> arguments, MetricReceiver metricReceiver) {
        this.linguistics = linguistics;
        this.requestedEmbedderId = embedderId;
        embedder = new Components.Selected<>("embedder", embedders, embedderId, true, arguments);
        var bc = embedder.component().batchingConfig();
        this.batcher = bc.isEnabled()
                ? new DynamicBatcher<>(bc.maxSize(), bc.maxDelay(), this::executeBatch) : null;
        this.batchSize = metricReceiver.declareGauge(ContainerMetrics.EMBEDDER_BATCH_SIZE.baseName());
        this.batchQueueTime = metricReceiver.declareGauge(ContainerMetrics.EMBEDDER_BATCH_QUEUE_TIME.baseName());
        this.batchCount = metricReceiver.declareCounter(ContainerMetrics.EMBEDDER_BATCH_COUNT.baseName());
    }

    private record BatchKey(String destination, Language language, TensorType targetType) {}
    private record EmbedInput(String text, Embedder.Context context, long enqueuedAtNanos) {}

    /** @return the requested embedder id. This will diverge from the selected embedder's id when executed in config-model */
    public Optional<String> requestedEmbedderId() { return Optional.of(requestedEmbedderId).filter(s -> !s.isEmpty()); }

    @Override
    public DataType setInputType(DataType inputType, TypeContext context) {
        super.setInputType(inputType, context);
        var outputType = getOutputType(context); // Cannot be determined from input
        validateInputAndOutput(inputType, outputType);
        return outputType;
    }

    @Override
    public DataType setOutputType(DataType outputType, TypeContext context) {
        super.setOutputType(null, outputType, TensorDataType.any(), context);
        var inputType = getInputType(context); // Cannot be determined from output
        validateInputAndOutput(inputType, outputType);
        return inputType;
    }

    private void validateInputAndOutput(DataType input, DataType output) {
        if (input != null) {
            if (! (input.isAssignableTo(DataType.STRING)) &&
                ! (input instanceof ArrayDataType array && array.getNestedType().isAssignableTo(DataType.STRING)))
                invalid("This requires either a string or array<string> input type, but got " + input.getName());
        }
        if (output != null) {
            var outputTensor = toTargetTensor(output);
            if ( ! validTarget(outputTensor))
                invalid("The embedding target field must either be a dense 1d tensor, a mapped 1d tensor, a mapped 2d tensor, " +
                        "an array of dense 1d tensors, or a mixed 2d or 3d tensor");
            if (outputTensor.rank() == 2 && outputTensor.mappedSubtype().rank() == 2) {
                if (embedder.arguments().size() != 1)
                    invalid("When the embedding target field is a 2d mapped tensor " +
                            "the name of the tensor dimension that corresponds to the input array elements must " +
                            "be given as a second argument to embed, e.g: ... | embed splade paragraph | ...");
                if ( ! outputTensor.mappedSubtype().dimensionNames().contains(embedder.arguments().get(0))) {
                    invalid("The dimension '" + embedder.arguments().get(0) + "' given to embed " +
                            "is not a sparse dimension of the target type " + outputTensor);

                }
            }
            if (outputTensor.rank() == 3) {
                if (embedder.arguments().size() != 1)
                    invalid("When the embedding target field is a 3d tensor " +
                            "the name of the tensor dimension that corresponds to the input array elements must " +
                            "be given as a second argument to embed, e.g: ... | embed colbert paragraph | ...");
                if ( ! outputTensor.mappedSubtype().dimensionNames().contains(embedder.arguments().get(0)))
                    invalid("The dimension '" + embedder.arguments().get(0) + "' given to embed " +
                            "is not a sparse dimension of the target type " + outputTensor);
            }
        }
        if (input != null && output != null) { // verify input/output consistency
            var outputTensor = toTargetTensor(output);
            if (input.isAssignableTo(DataType.STRING)
                && !(outputTensor.rank() == 1 || (outputTensor.rank() == 2 && outputTensor.mappedSubtype().rank() > 0)))
                invalid("Input is a string, so output must be a rank 1 tensor, or a rank 2 tensor with " +
                        "one mapped dimension, but got " + outputTensor);
            if ((input instanceof ArrayDataType)
                && !(outputTensor.rank() > 1 && outputTensor.mappedSubtype().rank() > 0))
                invalid("Input is an array, so output must be a rank 2 or 3 tensor with " +
                        "at least one mapped dimension, but got " + outputTensor);
        }
    }

    private void invalid(String message) {
        throw new VerificationException(this, message);
    }

    @Override
    public void setStatementOutput(DocumentType documentType, Field field) {
        destination = documentType.getName() + "." + field.getName();
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        if (context.getCurrentValue() == null) return;
        Tensor output;
        if (context.getCurrentValue().getDataType() == DataType.STRING) {
            output = embedSingleValue(context).orElse(null);
        }
        else if (context.getCurrentValue().getDataType() instanceof ArrayDataType arrayType
                 && arrayType.getNestedType() == DataType.STRING) {
            output = embedArrayValue(getOutputTensorType(), context);
        }
        else {
            throw new IllegalArgumentException("Embedding can only be done on string or string array fields, not " +
                                               context.getCurrentValue().getDataType());
        }
        if (output != null) {
            context.setCurrentValue(new TensorFieldValue(output));
        } else {
            context.setCurrentValue(null);
        }
    }

    private Optional<Tensor> embedSingleValue(ExecutionContext context) {
        StringFieldValue input = (StringFieldValue)context.getCurrentValue();
        if (input.getString().isBlank()) return Optional.empty();
        return Optional.of(embed(input.getString(), getOutputTensorType(), context));
    }

    @SuppressWarnings("unchecked")
    private Tensor embedArrayValue(TensorType targetType, ExecutionContext context) {
        var input = (Array<StringFieldValue>)context.getCurrentValue();
        if (targetType.rank() == 2) {
            if (targetType.indexedSubtype().rank() == 1) {
                var builder = MixedTensor.BoundBuilder.of(targetType);
                embedArrayValueToRank2Tensor(input, builder, context);
                return builder.build();
            } else if (targetType.mappedSubtype().rank() == 2) {
                var builder = Tensor.Builder.of(targetType);
                embedArrayValueToRank2MappedTensor(input, builder, context);
                return builder.build();
            } else {
                throw new IllegalArgumentException("Embedding an array into " + targetType + " is not supported");
            }
        } else {
            var builder = Tensor.Builder.of(targetType);
            embedArrayValueToRank3Tensor(input, builder, context);
            return builder.build();
        }
    }

    private void embedArrayValueToRank2Tensor(Array<StringFieldValue> input,
                                              MixedTensor.BoundBuilder builder,
                                              ExecutionContext context) {
        var indexedTexts = filterBlankTexts(input);
        if (indexedTexts.isEmpty()) return;
        var texts = indexedTexts.stream().map(IndexedText::text).toList();
        var embeddings = embedBatch(texts, builder.type().indexedSubtype(), context);
        for (int i = 0; i < embeddings.size(); i++) {
            var tensor = asIndexed1d(embeddings.get(i));
            var denseSubspaceBuilder = builder.denseSubspaceBuilder(TensorAddress.of(indexedTexts.get(i).index()));
            for (long j = 0; j < tensor.size(); j++) {
                denseSubspaceBuilder.cellByDirectIndex(j, tensor.get(j));
            }
        }
    }

    private void embedArrayValueToRank3Tensor(Array<StringFieldValue> input,
                                              Tensor.Builder builder,
                                              ExecutionContext context) {
        String outerMappedDimension = embedder.arguments().get(0);
        String innerMappedDimension = builder.type().mappedSubtype().dimensionNames().stream().filter(d -> !d.equals(outerMappedDimension)).findFirst().get();
        String indexedDimension = builder.type().indexedSubtype().dimensions().get(0).name();
        long indexedDimensionSize = builder.type().indexedSubtype().dimensions().get(0).size().get();
        var innerType = new TensorType.Builder(builder.type().valueType()).mapped(innerMappedDimension).indexed(indexedDimension,indexedDimensionSize).build();
        int innerMappedDimensionIndex = innerType.indexOfDimensionAsInt(innerMappedDimension);
        int indexedDimensionIndex = innerType.indexOfDimensionAsInt(indexedDimension);
        var indexedTexts = filterBlankTexts(input);
        if (indexedTexts.isEmpty()) return;
        var texts = indexedTexts.stream().map(IndexedText::text).toList();
        var embeddings = embedBatch(texts, innerType, context);
        for (int i = 0; i < embeddings.size(); i++) {
            var tensor = embeddings.get(i);
            int originalIndex = indexedTexts.get(i).index();
            for (Iterator<Tensor.Cell> cells = tensor.cellIterator(); cells.hasNext(); ) {
                Tensor.Cell cell = cells.next();
                builder.cell()
                       .label(outerMappedDimension, originalIndex)
                       .label(innerMappedDimension, cell.getKey().label(innerMappedDimensionIndex))
                       .label(indexedDimension, cell.getKey().numericLabel(indexedDimensionIndex))
                       .value(cell.getValue());
            }
        }
    }

    private void embedArrayValueToRank2MappedTensor(Array<StringFieldValue> input,
                                              Tensor.Builder builder,
                                              ExecutionContext context) {
        String outerMappedDimension = embedder.arguments().get(0);
        String innerMappedDimension = getOutputTensorType().mappedSubtype().dimensionNames().stream().filter(d -> !d.equals(outerMappedDimension)).findFirst().get();

        var innerType = new TensorType.Builder(getOutputTensorType().valueType()).mapped(innerMappedDimension).build();
        int innerMappedDimensionIndex = innerType.indexOfDimensionAsInt(innerMappedDimension);

        var indexedTexts = filterBlankTexts(input);
        if (indexedTexts.isEmpty()) return;
        var texts = indexedTexts.stream().map(IndexedText::text).toList();
        var embeddings = embedBatch(texts, innerType, context);
        for (int i = 0; i < embeddings.size(); i++) {
            var tensor = embeddings.get(i);
            int originalIndex = indexedTexts.get(i).index();
            for (Iterator<Tensor.Cell> cells = tensor.cellIterator(); cells.hasNext(); ) {
                Tensor.Cell cell = cells.next();
                builder.cell()
                        .label(outerMappedDimension, originalIndex)
                        .label(innerMappedDimension, cell.getKey().label(innerMappedDimensionIndex))
                        .value(cell.getValue());
            }
        }
    }

    private Tensor embed(String input, TensorType targetType, ExecutionContext context) {
        if (batcher != null) {
            return embedWithDynamicBatching(input, targetType, context);
        }
        return invokeEmbedder(ctx -> embedder.component().embed(input, ctx, targetType), context);
    }

    private Tensor embedWithDynamicBatching(String input, TensorType targetType, ExecutionContext context) {
        var language = context.resolveLanguage(linguistics);
        var key = new BatchKey(destination, language, targetType);
        var embedderContext = createEmbedderContext(context, language);
        var embedInput = new EmbedInput(input, embedderContext, System.nanoTime());
        return translateExceptions(() -> batcher.execute(key, embedInput));
    }

    private List<Tensor> executeBatch(BatchKey key, List<EmbedInput> inputs) {
        var texts = inputs.stream().map(EmbedInput::text).toList();
        var ctx = combineBatchContexts(inputs);
        var result = embedder.component().embed(texts, ctx, key.targetType());
        emitBatchMetrics(key, inputs);
        return result;
    }

    private void emitBatchMetrics(BatchKey key, List<EmbedInput> inputs) {
        if (batchSize == null) return;
        var point = new Point(Map.of("embedder", embedder.id(),
                                      "language", key.language().languageCode(),
                                      "destination", key.destination()));
        batchSize.sample(inputs.size(), point);
        batchCount.add(1, point);
        long now = System.nanoTime();
        for (var input : inputs) {
            batchQueueTime.sample((now - input.enqueuedAtNanos()) / 1_000_000.0, point);
        }
    }

    private static Embedder.Context combineBatchContexts(List<EmbedInput> inputs) {
        var first = inputs.get(0).context();
        var earliestDeadline = inputs.stream()
                .map(input -> input.context().getDeadline())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Comparator.comparing(InvocationContext.Deadline::asInstant))
                .orElse(null);
        var ctx = first.copy();
        if (earliestDeadline != null) {
            ctx.setDeadline(earliestDeadline);
        }
        return ctx;
    }

    private List<Tensor> embedBatch(List<String> texts, TensorType targetType, ExecutionContext context) {
        return invokeEmbedder(ctx -> embedder.component().embed(texts, ctx, targetType), context);
    }

    private Embedder.Context createEmbedderContext(ExecutionContext context, Language language) {
        var embedderContext = new Embedder.Context(destination, context.getCache())
                .setLanguage(language)
                .setEmbedderId(embedder.id());
        context.getDeadline().ifPresent(instant ->
                embedderContext.setDeadline(com.yahoo.language.process.InvocationContext.Deadline.of(instant)));
        return embedderContext;
    }

    private <T> T invokeEmbedder(Function<Embedder.Context, T> embedFn, ExecutionContext context) {
        var language = context.resolveLanguage(linguistics);
        var embedderContext = createEmbedderContext(context, language);
        return translateExceptions(() -> embedFn.apply(embedderContext));
    }

    private <T> T translateExceptions(Supplier<T> fn) {
        try {
            return fn.get();
        } catch (com.yahoo.language.process.OverloadException e) {
            throw new OverloadException(e.getMessage(), e);
        } catch (com.yahoo.language.process.TimeoutException e) {
            throw new TimeoutException(e.getMessage(), e);
        } catch (com.yahoo.language.process.InvalidInputException e) {
            throw new InvalidInputException(e.getMessage(), e);
        }
    }

    /**
     * Helper method that calls embed, checks that the result is a 1-d indexed tensor, and returns it as an IndexedTensor.
     *
     * @return the embedded tensor as an IndexedTensor
     * @throws IllegalArgumentException if the result is not a 1-d indexed tensor
     */
    private static IndexedTensor asIndexed1d(Tensor result) {
        if (!(result instanceof IndexedTensor indexedResult)) {
            throw new IllegalArgumentException("Expected embed to return an IndexedTensor, but got " +
                                             result.getClass().getSimpleName());
        }

        if (indexedResult.type().rank() != 1) {
            throw new IllegalArgumentException("Expected embed to return a 1-d tensor, but got rank " +
                                             indexedResult.type().rank());
        }

        if (!indexedResult.type().dimensions().get(0).isIndexed()) {
            throw new IllegalArgumentException("Expected embed to return an indexed tensor, but got " +
                                             indexedResult.type().dimensions().get(0).type());
        }

        return indexedResult;
    }

    private TensorType getOutputTensorType() {
        return ((TensorDataType)getOutputType()).getTensorType();
    }

    private static TensorType toTargetTensor(DataType dataType) {
        if (dataType instanceof ArrayDataType) return toTargetTensor(dataType.getNestedType());
        if  ( ! ( dataType instanceof TensorDataType))
            throw new IllegalArgumentException("Expected a tensor data type but got " + dataType);
        return ((TensorDataType)dataType).getTensorType();
    }

    private boolean validTarget(TensorType target) {
        if (target.rank() == 1) // indexed or mapped 1d tensor
            return true;
        if (target.rank() == 2 && target.indexedSubtype().rank() == 1)
            return true; // mixed 2d tensor
        if(target.rank() == 2 && target.mappedSubtype().rank() == 2)
            return true; // mapped 2d tensor
        if (target.rank() == 3 && target.indexedSubtype().rank() == 1)
            return true; // mixed 3d tensor
        return false;
    }

    @Override
    public String toString() { return "embed" + embedder.argumentsString(); }

    @Override
    public int hashCode() { return Objects.hash(EmbedExpression.class, embedder); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EmbedExpression other)) return false;
        return other.embedder.equals(this.embedder);
    }

    private static List<IndexedText> filterBlankTexts(Array<StringFieldValue> input) {
        var result = new ArrayList<IndexedText>(input.size());
        for (int i = 0; i < input.size(); i++) {
            var text = input.get(i).getString();
            if (!text.isBlank()) {
                result.add(new IndexedText(i, text));
            }
        }
        return result;
    }

    private record IndexedText(int index, String text) {}
}
