package com.yahoo.tensor;

import com.yahoo.tensor.functions.Reduce;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Microbenchmark of tensor operations.
 * 
 * @author bratseth
 */
public class TensorFunctionBenchmark {

    private final Random random = new Random();
    
    public void benchmark(int iterations) {
        List<Tensor> modelVectors = generateVectors(100, 300);
        Tensor queryVector = generateVectors(1, 300).get(0);
        dotProduct(queryVector, modelVectors, 10); // warmup
        long startTime = System.currentTimeMillis();
        dotProduct(queryVector, modelVectors, iterations);
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("Time per join: " + (totalTime / iterations) +  " ms");
    }

    private double dotProduct(Tensor tensor, List<Tensor> tensors, int iterations) {
        double result = 0;
        for (int i = 0 ; i < iterations; i++)
            result = dotProduct(tensor, tensors);
        return result;
    }

    private double dotProduct(Tensor tensor, List<Tensor> tensors) {
        double largest = Double.MIN_VALUE;
        for (Tensor tensorElement : tensors) {
            double dotProduct = tensor.join(tensorElement, (a, b) -> a * b).reduce(Reduce.Aggregator.sum).asDouble();
            if (dotProduct > largest)
                largest = dotProduct;
        }
        System.out.println(largest);
        return largest;
    }
    
    private List<Tensor> generateVectors(int vectorCount, int vectorSize) {
        List<Tensor> tensors = new ArrayList<>();
        TensorType type = new TensorType.Builder().mapped("x").build();
        for (int i = 0; i < vectorCount; i++) {
            MapTensorBuilder builder = new MapTensorBuilder(type);
            for (int j = 0; j < vectorSize; j++) {
                builder.cell().label("x", String.valueOf(j)).value(random.nextDouble());
            }
            tensors.add(builder.build());
        }
        return tensors;
    }
    
    public static void main(String[] args) {
        // Was: 150 ms
        // After adding type: 300 ms
        new TensorFunctionBenchmark().benchmark(100);
    }

}
