// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.yahoo.tensor.evaluation.MapEvaluationContext;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.ConstantTensor;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Microbenchmark of tensor operations.
 *
 * @author bratseth
 */
public class TensorFunctionBenchmark {

    private final static Random random = new Random();

    public double benchmark(int iterations, List<Tensor> modelVectors, TensorType.Dimension.Type dimensionType,
                            boolean extraSpace) {
        Tensor queryVector = vectors(1, 300, dimensionType).get(0);
        if (extraSpace) {
            queryVector = queryVector.multiply(unitVector("j"));
            modelVectors = modelVectors.stream().map(t -> t.multiply(unitVector("k"))).collect(Collectors.toList());
        }
        dotProduct(queryVector, modelVectors, Math.max(iterations/10, 10)); // warmup
        System.gc();
        long startTime = System.currentTimeMillis();
        dotProduct(queryVector, modelVectors, iterations);
        long totalTime = System.currentTimeMillis() - startTime;
        return (double)totalTime / (double)iterations;
    }

    private Tensor unitVector(String dimension) {
        return Tensor.Builder.of(new TensorType.Builder().indexed(dimension, 1).build())
                .cell().label(dimension, 0).value(1).build();
    }

    private double dotProduct(Tensor tensor, List<Tensor> tensors, int iterations) {
        double result = 0;
        for (int i = 0 ; i < iterations; i++)
            result = dotProduct(tensor, tensors);
        return result;
    }

    private double dotProduct(Tensor tensor, List<Tensor> tensors) {
        double largest = Double.MIN_VALUE;
        TensorFunction dotProductFunction = new Reduce(new Join(new ConstantTensor(tensor),
                                                                new VariableTensor("argument"), (a, b) -> a * b),
                                                       Reduce.Aggregator.sum).toPrimitive();
        MapEvaluationContext context = new MapEvaluationContext();

        for (Tensor tensorElement : tensors) { // tensors.size() = 1 for larger tensor
            context.put("argument", tensorElement);
            double dotProduct = dotProductFunction.evaluate(context).asDouble();
            if (dotProduct > largest) {
                largest = dotProduct;
            }
        }
        return largest;
    }

    private static List<Tensor> vectors(int vectorCount, int vectorSize, TensorType.Dimension.Type dimensionType) {
        List<Tensor> tensors = new ArrayList<>();
        TensorType type = vectorType(new TensorType.Builder(), "x", dimensionType, vectorSize);
        for (int i = 0; i < vectorCount; i++) {
            Tensor.Builder builder = Tensor.Builder.of(type);
            for (int j = 0; j < vectorSize; j++) {
                builder.cell().label("x", String.valueOf(j)).value(random.nextDouble());
            }
            tensors.add(builder.build());
        }
        return tensors;
    }

    private static List<Tensor> matrix(int vectorCount, int vectorSize, TensorType.Dimension.Type dimensionType) {
        TensorType.Builder typeBuilder = new TensorType.Builder();
        typeBuilder.dimension("i", dimensionType == TensorType.Dimension.Type.indexedBound ? TensorType.Dimension.Type.indexedUnbound : dimensionType);
        vectorType(typeBuilder, "x", dimensionType, vectorSize);
        Tensor.Builder builder = Tensor.Builder.of(typeBuilder.build());
        for (int i = 0; i < vectorCount; i++) {
            for (int j = 0; j < vectorSize; j++) {
                builder.cell()
                        .label("i", String.valueOf(i))
                        .label("x", String.valueOf(j))
                        .value(random.nextDouble());
            }
        }
        return Collections.singletonList(builder.build());
    }

    private static TensorType vectorType(TensorType.Builder builder, String name, TensorType.Dimension.Type type, int size) {
        switch (type) {
            case mapped: builder.mapped(name); break;
            case indexedUnbound: builder.indexed(name); break;
            case indexedBound: builder.indexed(name, size); break;
            default: throw new IllegalArgumentException("Dimension type " + type + " not supported");
        }
        return builder.build();
    }

    public static void main(String[] args) {
        double time = 0;

        // ---------------- Mapped with extra space (sidesteps current special-case optimizations):
        // 7.8 ms
        time = new TensorFunctionBenchmark().benchmark(1000, vectors(100, 300, TensorType.Dimension.Type.mapped), TensorType.Dimension.Type.mapped, true);
        System.out.printf("Mapped vectors, x space  time per join: %1$8.3f ms\n", time);
        // 7.7 ms
        time = new TensorFunctionBenchmark().benchmark(1000, matrix(100, 300, TensorType.Dimension.Type.mapped), TensorType.Dimension.Type.mapped, true);
        System.out.printf("Mapped matrix, x space   time per join: %1$8.3f ms\n", time);

        // ---------------- Mapped:
        // 2.1 ms
        time = new TensorFunctionBenchmark().benchmark(5000, vectors(100, 300, TensorType.Dimension.Type.mapped), TensorType.Dimension.Type.mapped, false);
        System.out.printf("Mapped vectors,          time per join: %1$8.3f ms\n", time);
        // 7.0 ms
        time = new TensorFunctionBenchmark().benchmark(1000, matrix(100, 300, TensorType.Dimension.Type.mapped), TensorType.Dimension.Type.mapped, false);
        System.out.printf("Mapped matrix,           time per join: %1$8.3f ms\n", time);

        // ---------------- Indexed (unbound) with extra space (sidesteps current special-case optimizations):
        // 14.5 ms
        time = new TensorFunctionBenchmark().benchmark(500, vectors(100, 300, TensorType.Dimension.Type.indexedUnbound), TensorType.Dimension.Type.indexedUnbound, true);
        System.out.printf("Indexed vectors, x space time per join: %1$8.3f ms\n", time);
        // 8.9 ms
        time = new TensorFunctionBenchmark().benchmark(500, matrix(100, 300, TensorType.Dimension.Type.indexedUnbound), TensorType.Dimension.Type.indexedUnbound, true);
        System.out.printf("Indexed matrix, x space  time per join: %1$8.3f ms\n", time);

        // ---------------- Indexed unbound:
        // 0.14 ms
        time = new TensorFunctionBenchmark().benchmark(50000, vectors(100, 300, TensorType.Dimension.Type.indexedUnbound), TensorType.Dimension.Type.indexedUnbound, false);
        System.out.printf("Indexed unbound vectors, time per join: %1$8.3f ms\n", time);
        // 0.44 ms
        time = new TensorFunctionBenchmark().benchmark(50000, matrix(100, 300, TensorType.Dimension.Type.indexedUnbound), TensorType.Dimension.Type.indexedUnbound, false);
        System.out.printf("Indexed unbound matrix,  time per join: %1$8.3f ms\n", time);

        // ---------------- Indexed bound:
        // 0.32 ms
        time = new TensorFunctionBenchmark().benchmark(50000, vectors(100, 300, TensorType.Dimension.Type.indexedBound), TensorType.Dimension.Type.indexedBound, false);
        System.out.printf("Indexed bound vectors,   time per join: %1$8.3f ms\n", time);
        // 0.44 ms
        time = new TensorFunctionBenchmark().benchmark(50000, matrix(100, 300, TensorType.Dimension.Type.indexedBound), TensorType.Dimension.Type.indexedBound, false);
        System.out.printf("Indexed bound matrix,    time per join: %1$8.3f ms\n", time);
    }

}
