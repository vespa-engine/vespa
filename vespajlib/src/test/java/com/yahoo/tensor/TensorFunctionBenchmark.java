// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.yahoo.tensor.evaluation.MapEvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.ConstantTensor;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * Microbenchmark of tensor operations.
 *
 * @author bratseth
 */
public class TensorFunctionBenchmark {

    private final static Random random = new Random();

    public double benchmark(int iterations, int vectorSize, List<Tensor> modelVectors, TensorType.Dimension.Type dimensionType,
                            boolean extraSpace, boolean stringLabels) {
        Tensor queryVector = vectors(1, vectorSize, dimensionType, stringLabels).get(0);
        if (extraSpace) {
            queryVector = queryVector.multiply(unitVector("j"));
            modelVectors = modelVectors.stream().map(t -> t.multiply(unitVector("k"))).toList();
        }
        dotProduct(queryVector, modelVectors, Math.max(iterations / 10, 10)); // warmup
        System.gc();
        long startTime = System.currentTimeMillis();
        dotProduct(queryVector, modelVectors, iterations);
        long totalTime = System.currentTimeMillis() - startTime;
        return (double) totalTime / (double) iterations;
    }

    private Tensor unitVector(String dimension) {
        return Tensor.Builder.of(new TensorType.Builder().indexed(dimension, 1).build())
                .cell().label(dimension, 0).value(1).build();
    }

    private double dotProduct(Tensor tensor, List<Tensor> tensors, int iterations) {
        double result = 0;
        for (int i = 0; i < iterations; i++)
            result = dotProduct(tensor, tensors);
        return result;
    }

    private double dotProduct(Tensor tensor, List<Tensor> tensors) {
        double largest = Double.MIN_VALUE;
        TensorFunction<Name> dotProductFunction = new Reduce<>(
                new Join<>(new ConstantTensor<>(tensor),
                        new VariableTensor<>("argument"), (a, b) -> a * b
                ),
                Reduce.Aggregator.sum
        ).toPrimitive();
        MapEvaluationContext<Name> context = new MapEvaluationContext<>();

        for (Tensor tensorElement : tensors) { // tensors.size() = 1 for larger tensor
            context.put("argument", tensorElement);
            double dotProduct = dotProductFunction.evaluate(context).asDouble();
            if (dotProduct > largest) {
                largest = dotProduct;
            }
        }
        return largest;
    }

    private static List<Tensor> vectors(int vectorCount, int vectorSize, TensorType.Dimension.Type dimensionType, boolean stringLabels) {
        List<Tensor> tensors = new ArrayList<>();
        TensorType type = vectorType(new TensorType.Builder(), "x", dimensionType, vectorSize);
        for (int i = 0; i < vectorCount; i++) {
            Tensor.Builder builder = Tensor.Builder.of(type);
            for (int j = 0; j < vectorSize; j++) {
                if (stringLabels)
                    builder.cell().label("x", "l" + j).value(random.nextDouble());
                else
                    builder.cell().label("x", j).value(random.nextDouble());
            }
            tensors.add(builder.build());
        }
        return tensors;
    }

    private static List<Tensor> matrix(int vectorCount, int vectorSize, TensorType.Dimension.Type dimensionType, boolean stringLabels) {
        TensorType.Builder typeBuilder = new TensorType.Builder();
        typeBuilder.dimension(
                "i",
                dimensionType == TensorType.Dimension.Type.indexedBound ? TensorType.Dimension.Type.indexedUnbound : dimensionType
        );
        vectorType(typeBuilder, "x", dimensionType, vectorSize);
        Tensor.Builder builder = Tensor.Builder.of(typeBuilder.build());
        for (int i = 0; i < vectorCount; i++) {
            for (int j = 0; j < vectorSize; j++) {
                if (stringLabels) {
                    builder.cell()
                            .label("i", "l" + i)
                            .label("x", "l" + j)
                            .value(random.nextDouble());
                } else {
                    builder.cell()
                            .label("i", i)
                            .label("x", j)
                            .value(random.nextDouble());
                }
            }
        }
        return List.of(builder.build());
    }

    private static TensorType vectorType(TensorType.Builder builder, String name, TensorType.Dimension.Type type, int size) {
        switch (type) {
            case mapped:
                builder.mapped(name);
                break;
            case indexedUnbound:
                builder.indexed(name);
                break;
            case indexedBound:
                builder.indexed(name, size);
                break;
            default:
                throw new IllegalArgumentException("Dimension type " + type + " not supported");
        }
        return builder.build();
    }

    public static void main(String[] args) {
        double time = 0;
        // Important to use size larger than in Label.SMALL_INDEX_LABELS for more comprehensive benchmark
        int vectorSize = 2000;
        boolean stringLabels = true;

        // ---------------- Indexed unbound:
        time = new TensorFunctionBenchmark().benchmark(
                5000, vectorSize, vectors(100, vectorSize, TensorType.Dimension.Type.indexedUnbound, false),
                TensorType.Dimension.Type.indexedUnbound, false, false
        );
        System.out.printf("Indexed unbound vectors,                     time per join: %1$8.3f ms\n", time);
        time = new TensorFunctionBenchmark().benchmark(
                5000, vectorSize, matrix(100, vectorSize, TensorType.Dimension.Type.indexedUnbound, false),
                TensorType.Dimension.Type.indexedUnbound, false, false
        );
        System.out.printf("Indexed unbound matrix,                      time per join: %1$8.3f ms\n", time);

        // ---------------- Indexed bound:
        time = new TensorFunctionBenchmark().benchmark(
                5000, vectorSize, vectors(100, vectorSize, TensorType.Dimension.Type.indexedBound, false),
                TensorType.Dimension.Type.indexedBound, false, false
        );
        System.out.printf("Indexed bound vectors,                       time per join: %1$8.3f ms\n", time);

        time = new TensorFunctionBenchmark().benchmark(
                5000, vectorSize, matrix(100, vectorSize, TensorType.Dimension.Type.indexedBound, false),
                TensorType.Dimension.Type.indexedBound, false, false
        );
        System.out.printf("Indexed bound matrix,                        time per join: %1$8.3f ms\n", time);

        // ---------------- Mapped:
        time = new TensorFunctionBenchmark().benchmark(
                500, vectorSize, vectors(100, vectorSize, TensorType.Dimension.Type.mapped, false),
                TensorType.Dimension.Type.mapped, false, false
        );
        System.out.printf("Mapped vectors,                              time per join: %1$8.3f ms\n", time);

        time = new TensorFunctionBenchmark().benchmark(
                100, vectorSize, matrix(100, vectorSize, TensorType.Dimension.Type.mapped, false),
                TensorType.Dimension.Type.mapped, false, false
        );
        System.out.printf("Mapped matrix,                               time per join: %1$8.3f ms\n", time);

        // ---------------- Mapped with string labels:
        time = new TensorFunctionBenchmark().benchmark(
                500, vectorSize, vectors(100, vectorSize, TensorType.Dimension.Type.mapped, true),
                TensorType.Dimension.Type.mapped, false, true
        );
        System.out.printf("Mapped vectors with string labels,           time per join: %1$8.3f ms\n", time);

        time = new TensorFunctionBenchmark().benchmark(
                100, vectorSize, matrix(100, vectorSize, TensorType.Dimension.Type.mapped, true),
                TensorType.Dimension.Type.mapped, false, true
        );
        System.out.printf("Mapped matrix with string labels,            time per join: %1$8.3f ms\n", time);

        // ---------------- Indexed (unbound) with extra space (sidesteps current special-case optimizations):
        time = new TensorFunctionBenchmark().benchmark(
                50, vectorSize, vectors(100, vectorSize, TensorType.Dimension.Type.indexedUnbound, false),
                TensorType.Dimension.Type.indexedUnbound, true, false
        );
        System.out.printf("Indexed vectors, x space                     time per join: %1$8.3f ms\n", time);

        time = new TensorFunctionBenchmark().benchmark(
                50, vectorSize, matrix(100, vectorSize, TensorType.Dimension.Type.indexedUnbound, false),
                TensorType.Dimension.Type.indexedUnbound, true, false
        );
        System.out.printf("Indexed matrix, x space                      time per join: %1$8.3f ms\n", time);

        // ---------------- Mapped with extra space (sidesteps current special-case optimizations) with string labels:
        time = new TensorFunctionBenchmark().benchmark(
                100, vectorSize, vectors(100, vectorSize, TensorType.Dimension.Type.mapped, true),
                TensorType.Dimension.Type.mapped, true, true
        );
        System.out.printf("Mapped vectors, x space with string labels   time per join: %1$8.3f ms\n", time);

        time = new TensorFunctionBenchmark().benchmark(
                100, vectorSize, matrix(100, vectorSize, TensorType.Dimension.Type.mapped, true),
                TensorType.Dimension.Type.mapped, true, true
        );
        System.out.printf("Mapped matrix, x space with string labels    time per join: %1$8.3f ms\n", time);
    }
}
