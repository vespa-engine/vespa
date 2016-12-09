package com.yahoo.tensor;

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
        // TODO: Build function before applying, support context
        // TensorFunction dotProduct = new Reduce(new Join(), Reduce.Aggregator.max);
        for (Tensor tensorElement : tensors) { // tensors.size() = 1 for larger tensor
            Tensor result = tensor.join(tensorElement, (a, b) -> a * b).reduce(Reduce.Aggregator.sum, "x");
            double dotProduct = result.reduce(Reduce.Aggregator.max).asDouble(); // for larger tensor
            if (dotProduct > largest) {
                largest = dotProduct;
            }
        }
        return largest;
    }

    private static List<Tensor> generateVectors(int vectorCount, int vectorSize, 
                                                TensorType.Dimension.Type dimensionType) {
        List<Tensor> tensors = new ArrayList<>();
        TensorType type = new TensorType.Builder().dimension("x", dimensionType).build();
        for (int i = 0; i < vectorCount; i++) {
            // TODO: Avoid this by creating a (type independent) Tensor.Builder
            if (dimensionType == TensorType.Dimension.Type.mapped) {
                MapTensor.Builder builder = new MapTensor.Builder(type);
                for (int j = 0; j < vectorSize; j++) {
                    builder.cell().label("x", String.valueOf(j)).value(random.nextDouble());
                }
                tensors.add(builder.build());
            }
            else {
                IndexedTensor.Builder builder = new IndexedTensor.Builder(type);
                for (int j = 0; j < vectorSize; j++) {
                    builder.set(random.nextDouble(), j);
                }
                tensors.add(builder.build());
            }
        }
        return tensors;
    }

    private static List<Tensor> generateMatrix(int vectorCount, int vectorSize,
                                               TensorType.Dimension.Type dimensionType) {
        List<Tensor> tensors = new ArrayList<>();
        TensorType type = new TensorType.Builder().dimension("i", dimensionType).dimension("x", dimensionType).build();
        // TODO: Avoid this by creating a (type independent) Tensor.Builder
        if (dimensionType == TensorType.Dimension.Type.mapped) {
            MapTensor.Builder builder = new MapTensor.Builder(type);
            for (int i = 0; i < vectorCount; i++) {
                for (int j = 0; j < vectorSize; j++) {
                    builder.cell()
                            .label("i", String.valueOf(i))
                            .label("x", String.valueOf(j))
                            .value(random.nextDouble());
                }
            }
            tensors.add(builder.build());
        }
        else {
            IndexedTensor.Builder builder = new IndexedTensor.Builder(type);
            for (int i = 0; i < vectorCount; i++) {
                for (int j = 0; j < vectorSize; j++) {
                    builder.set(random.nextDouble(), i, j);
                }
            }
            tensors.add(builder.build());
        }
        return tensors; // only one tensor in the list.
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
