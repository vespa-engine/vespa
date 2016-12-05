package com.yahoo.tensor;

import com.yahoo.tensor.functions.Reduce;

import java.util.*;

/**
 * Microbenchmark of tensor operations.
 * 
 * @author bratseth
 */
public class TensorFunctionBenchmark {

    private final static Random random = new Random();
    
    public double benchmark(int iterations, List<Tensor> modelVectors) {
        Tensor queryVector = generateVectors(1, 300).get(0);
        dotProduct(queryVector, modelVectors, 10); // warmup
        long startTime = System.currentTimeMillis();
        dotProduct(queryVector, modelVectors, iterations);
        long totalTime = System.currentTimeMillis() - startTime;
        return totalTime / iterations;
    }

    private double dotProduct(Tensor tensor, List<Tensor> tensors, int iterations) {
        double result = 0;
        for (int i = 0 ; i < iterations; i++)
            result = dotProduct(tensor, tensors);
        return result;
    }

    private double dotProduct(Tensor tensor, List<Tensor> tensors) {
        double largest = Double.MIN_VALUE;
        for (Tensor tensorElement : tensors) { // tensors.size() = 1 for larger tensor
            Tensor result = tensor.join(tensorElement, (a, b) -> a * b).reduce(Reduce.Aggregator.sum, "x");
            double dotProduct = result.reduce(Reduce.Aggregator.max).asDouble(); // for larger tensor
            if (dotProduct > largest) {
                largest = dotProduct;
            }
        }
        System.out.println(largest);
        return largest;
    }

    private static List<Tensor> generateVectors(int vectorCount, int vectorSize) {
        List<Tensor> tensors = new ArrayList<>();
        TensorType type = new TensorType.Builder().mapped("x").build();
        for (int i = 0; i < vectorCount; i++) {
            MapTensor.Builder builder = new MapTensor.Builder(type);
            for (int j = 0; j < vectorSize; j++) {
                builder.cell().label("x", String.valueOf(j)).value(random.nextDouble());
            }
            tensors.add(builder.build());
        }
        return tensors;
    }

    private static List<Tensor> generateVectorsInOneTensor(int vectorCount, int vectorSize) {
        List<Tensor> tensors = new ArrayList<>();
        TensorType type = new TensorType.Builder().mapped("i").mapped("x").build();
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
        return tensors; // only one tensor in the list.
    }

    public static void main(String[] args) {
        // Was: 150 ms
        // After adding type: 300 ms
        // After sorting dimensions: 100 ms
        // After special-casing single space: 4 ms
        double timeperJoin = new TensorFunctionBenchmark().benchmark(100, generateVectors(100, 300));

        // This benchmark should be as fast as fast as the previous. Currently it is not by a factor of 600
        double timePerJoinOneTensor = new TensorFunctionBenchmark().benchmark(20, generateVectorsInOneTensor(100, 300));

        System.out.println("Time per join: " + timeperJoin +  " ms");
        System.out.println("Time per join, one tensor: " + timePerJoinOneTensor +  " ms");
    }

}
