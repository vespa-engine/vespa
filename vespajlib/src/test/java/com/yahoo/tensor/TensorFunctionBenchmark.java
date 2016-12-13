package com.yahoo.tensor;

import com.yahoo.tensor.evaluation.MapEvaluationContext;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.ConstantTensor;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.*;

/**
 * Microbenchmark of tensor operations.
 * 
 * @author bratseth
 */
public class TensorFunctionBenchmark {

    private final static Random random = new Random();
    
    public double benchmark(int iterations, List<Tensor> modelVectors, TensorType.Dimension.Type dimensionType) {
        Tensor queryVector = generateVectors(1, 300, dimensionType).get(0);
        dotProduct(queryVector, modelVectors, Math.max(iterations/10, 10)); // warmup
        long startTime = System.currentTimeMillis();
        dotProduct(queryVector, modelVectors, iterations);
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
                                                       Reduce.Aggregator.max).toPrimitive();
        MapEvaluationContext context = new MapEvaluationContext();
        
        for (Tensor tensorElement : tensors) { // tensors.size() = 1 for larger tensor
            context.put("argument", tensorElement);
            double dotProduct = dotProductFunction.evaluate(context).asDouble();
            if (dotProduct > largest) {
                largest = dotProduct;
            }
        }
        System.out.println(largest);
        return largest;
    }

    private static List<Tensor> generateVectors(int vectorCount, int vectorSize, 
                                                TensorType.Dimension.Type dimensionType) {
        List<Tensor> tensors = new ArrayList<>();
        TensorType type = new TensorType.Builder().dimension("x", dimensionType).build();
        for (int i = 0; i < vectorCount; i++) {
            Tensor.Builder builder = Tensor.Builder.of(type);
            for (int j = 0; j < vectorSize; j++) {
                builder.cell().label("x", String.valueOf(j)).value(random.nextDouble());
            }
            tensors.add(builder.build());
        }
        return tensors;
    }

    private static List<Tensor> generateMatrix(int vectorCount, int vectorSize, 
                                               TensorType.Dimension.Type dimensionType) {
        TensorType type = new TensorType.Builder().dimension("i", dimensionType).dimension("x", dimensionType).build();
        Tensor.Builder builder = Tensor.Builder.of(type);
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

    public static void main(String[] args) {
        double time;
        
        // ---------------- Mapped:
        // Initial: 150 ms
        // - After adding type: 300 ms
        // - After sorting dimensions: 100 ms
        // - After special-casing single space: 2.4 ms
        time = new TensorFunctionBenchmark().benchmark(5000, generateVectors(100, 300, TensorType.Dimension.Type.mapped), TensorType.Dimension.Type.mapped);
        System.out.printf("Mapped vectors,  time per join: %1$8.3f ms\n", time);
        // Initial: 760 ms
        time = new TensorFunctionBenchmark().benchmark(10, generateMatrix(100, 300, TensorType.Dimension.Type.mapped), TensorType.Dimension.Type.mapped);
        System.out.printf("Mapped matrix,   time per join: %1$8.3f ms\n", time);

        // ---------------- Indexed:
        // Initial: 718 ms
        // - After special casing join: 3.6 ms
        // - After special-casing reduce: 0.80 ms
        // - After create IndexedTensor without builder: 0.41 ms
        time = new TensorFunctionBenchmark().benchmark(5000, generateVectors(100, 300, TensorType.Dimension.Type.indexedUnbound),TensorType.Dimension.Type.indexedUnbound);
        System.out.printf("Indexed vectors, time per join: %1$8.3f ms\n", time);
        // Initial: 3500 ms
        // time = new TensorFunctionBenchmark().benchmark(10, generateMatrix(100, 300, TensorType.Dimension.Type.indexedUnbound), TensorType.Dimension.Type.indexedUnbound);
        // System.out.printf("Indexed matrix,  time per join: %1$8.3f ms\n", time);
    }

}
