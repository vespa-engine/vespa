// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.yahoo.tensor.evaluation.MapEvaluationContext;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.ConstantTensor;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Microbenchmark of a "dot product" of two mapped rank 2 tensors
 *
 * @author bratseth
 */
public class MatrixDotProductBenchmark {

    private final static Random random = new Random();

    public double benchmark(int iterations, List<Tensor> modelMatrixes, TensorType.Dimension.Type dimensionType) {
        Tensor queryMatrix = matrix(1, 20, dimensionType).get(0);
        dotProduct(queryMatrix, modelMatrixes, Math.max(iterations/10, 10)); // warmup
        System.gc();
        long startTime = System.currentTimeMillis();
        dotProduct(queryMatrix, modelMatrixes, iterations);
        long totalTime = System.currentTimeMillis() - startTime;
        return (double)totalTime / (double)iterations;
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

    private static List<Tensor> matrix(int dimension1Size, int dimension2Size, TensorType.Dimension.Type dimensionType) {
        TensorType.Builder typeBuilder = new TensorType.Builder();
        addDimension(typeBuilder, "i", dimensionType, dimension1Size);
        addDimension(typeBuilder, "j", dimensionType, dimension2Size);
        Tensor.Builder builder = Tensor.Builder.of(typeBuilder.build());
        for (int i = 0; i < dimension1Size; i++) {
            for (int j = 0; j < dimension2Size; j++) {
                builder.cell()
                        .label("i", String.valueOf("label" + i))
                        .label("j", String.valueOf("label" + j))
                        .value(random.nextDouble());
            }
        }
        return Collections.singletonList(builder.build());
    }

    private static void addDimension(TensorType.Builder builder, String name, TensorType.Dimension.Type type, int size) {
        switch (type) {
            case mapped: builder.mapped(name); break;
            case indexedUnbound: builder.indexed(name); break;
            case indexedBound: builder.indexed(name, size); break;
            default: throw new IllegalArgumentException("Dimension type " + type + " not supported");
        }
    }

    public static void main(String[] args) {
        double time = new MatrixDotProductBenchmark().benchmark(10000, matrix(10, 55, TensorType.Dimension.Type.mapped), TensorType.Dimension.Type.mapped);
        System.out.printf("Matrixes, 10*55 size matrixes. Time per sum(join): %1$8.3f ms\n", time);
    }

}
