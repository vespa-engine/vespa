// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor;

import com.yahoo.tensor.evaluation.MapEvaluationContext;
import com.yahoo.tensor.evaluation.Name;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.ConstantTensor;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.TensorFunction;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    public double benchmark(int iterations, List<Tensor> modelVectors, TensorType.Dimension.Type dimensionType,
                            boolean extraSpace) {
        Tensor queryVector = vectors(1, 300, dimensionType).get(0);
        if (extraSpace) {
            queryVector = queryVector.multiply(unitVector("j"));
            modelVectors = modelVectors.stream().map(t -> t.multiply(unitVector("k"))).toList();
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
        TensorFunction<Name> dotProductFunction = new Reduce<>(new Join<>(new ConstantTensor<>(tensor),
                                                                          new VariableTensor<>("argument"), (a, b) -> a * b),
                                                               Reduce.Aggregator.sum).toPrimitive();
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

    private static List<Tensor> vectors(int vectorCount, int vectorSize, TensorType.Dimension.Type dimensionType) {
        List<Tensor> tensors = new ArrayList<>();
        TensorType type = vectorType(new TensorType.Builder(), "x", dimensionType, vectorSize);
        for (int i = 0; i < vectorCount; i++) {
            Tensor.Builder builder = Tensor.Builder.of(type);
            for (int j = 0; j < vectorSize; j++) {
                builder.cell().label("x", j).value(random.nextDouble());
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
                        .label("i", i)
                        .label("x", j)
                        .value(random.nextDouble());
            }
        }
        return List.of(builder.build());
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

    public static String getProcessorName() {
        String os = System.getProperty("os.name").toLowerCase();
        String command;

        if (os.contains("win")) {
            // Windows command to get CPU name
            command = "wmic cpu get Name";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            // Linux command to get CPU name
            command = "cat /proc/cpuinfo | grep 'model name' | head -1";
        } else if (os.contains("mac")) {
            // MacOS command to get CPU name
            command = "sysctl -n machdep.cpu.brand_string";
        } else {
            return "Unknown Processor";
        }

        try {
            var process = Runtime.getRuntime().exec(command);
            var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // Return the first meaningful line of output
                if (!line.trim().isEmpty() && !line.toLowerCase().contains("name")) {
                    return line.trim();
                }
            }
        } catch (IOException e) {
            System.err.println("An error occurred while retrieving the processor name: " + e.getMessage());
        }

        return "Unknown Processor";
    }

    public static String getCPU() {
        String os = System.getProperty("os.name").toLowerCase();
        String command;

        if (os.contains("win")) {
            command = "wmic cpu get Name";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            command = "cat /proc/cpuinfo | grep 'model name' | head -1";
        } else if (os.contains("mac")) {
            command = "sysctl -n machdep.cpu.brand_string";
        } else {
            return "Unknown Processor";
        }

        try {
            var process = Runtime.getRuntime().exec(command);
            var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // Return the first meaningful line of output
                if (!line.trim().isEmpty() && !line.toLowerCase().contains("name")) {
                    return line.trim();
                }
            }
        } catch (IOException e) {
            System.err.println("An error occurred while retrieving the processor name: " + e.getMessage());
        }

        return "Unknown Processor";
    }
    
    private static String generateFileName() {
        var cpu = getCPU().replace(" ", "-");
        int cores = Runtime.getRuntime().availableProcessors();
        var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")).replace(":", "-");
        var fileName = String.format("%s-%dc_%s.txt", cpu, cores, timestamp);
        return fileName;
    }

    public static void main(String[] args) {
        var fileName = generateFileName();
        var filePath = "vespajlib/src/test/resources/TensorFunctionBenchmark/" + fileName;

        try (var writer = new PrintWriter(new FileWriter(filePath))) {
            double time;
            String result;

            // ---------------- Indexed Unbound:
            time = new TensorFunctionBenchmark().benchmark(50000, vectors(100, 300, TensorType.Dimension.Type.indexedUnbound), TensorType.Dimension.Type.indexedUnbound, false);
            result = String.format("Indexed unbound vectors, time per join: %1$8.3f ms\n", time);
            System.out.print(result);
            writer.print(result);

            time = new TensorFunctionBenchmark().benchmark(50000, matrix(100, 300, TensorType.Dimension.Type.indexedUnbound), TensorType.Dimension.Type.indexedUnbound, false);
            result = String.format("Indexed unbound matrix,  time per join: %1$8.3f ms\n", time);
            System.out.print(result);
            writer.print(result);

            // ---------------- Indexed Bound:
            time = new TensorFunctionBenchmark().benchmark(50000, vectors(100, 300, TensorType.Dimension.Type.indexedBound), TensorType.Dimension.Type.indexedBound, false);
            result = String.format("Indexed bound vectors,   time per join: %1$8.3f ms\n", time);
            System.out.print(result);
            writer.print(result);

            time = new TensorFunctionBenchmark().benchmark(50000, matrix(100, 300, TensorType.Dimension.Type.indexedBound), TensorType.Dimension.Type.indexedBound, false);
            result = String.format("Indexed bound matrix,    time per join: %1$8.3f ms\n", time);
            System.out.print(result);
            writer.print(result);

            // ---------------- Mapped:
            time = new TensorFunctionBenchmark().benchmark(5000, vectors(100, 300, TensorType.Dimension.Type.mapped), TensorType.Dimension.Type.mapped, false);
            result = String.format("Mapped vectors,          time per join: %1$8.3f ms\n", time);
            System.out.print(result);
            writer.print(result);

            time = new TensorFunctionBenchmark().benchmark(1000, matrix(100, 300, TensorType.Dimension.Type.mapped), TensorType.Dimension.Type.mapped, false);
            result = String.format("Mapped matrix,           time per join: %1$8.3f ms\n", time);
            System.out.print(result);
            writer.print(result);

            // ---------------- Indexed (unbound) with extra space:
            time = new TensorFunctionBenchmark().benchmark(500, vectors(100, 300, TensorType.Dimension.Type.indexedUnbound), TensorType.Dimension.Type.indexedUnbound, true);
            result = String.format("Indexed vectors, x space time per join: %1$8.3f ms\n", time);
            System.out.print(result);
            writer.print(result);

            time = new TensorFunctionBenchmark().benchmark(500, matrix(100, 300, TensorType.Dimension.Type.indexedUnbound), TensorType.Dimension.Type.indexedUnbound, true);
            result = String.format("Indexed matrix, x space  time per join: %1$8.3f ms\n", time);
            System.out.print(result);
            writer.print(result);

            // ---------------- Mapped with extra space:
            time = new TensorFunctionBenchmark().benchmark(1000, vectors(100, 300, TensorType.Dimension.Type.mapped), TensorType.Dimension.Type.mapped, true);
            result = String.format("Mapped vectors, x space  time per join: %1$8.3f ms\n", time);
            System.out.print(result);
            writer.print(result);

            time = new TensorFunctionBenchmark().benchmark(1000, matrix(100, 300, TensorType.Dimension.Type.mapped), TensorType.Dimension.Type.mapped, true);
            result = String.format("Mapped matrix, x space   time per join: %1$8.3f ms\n", time);
            System.out.print(result);
            writer.print(result);

            /*
            2.4Ghz Intel Core i9, Macbook Pro 2019
            Indexed unbound vectors, time per join:    0,066 ms
            Indexed unbound matrix,  time per join:    0,108 ms
            Indexed bound vectors,   time per join:    0,068 ms
            Indexed bound matrix,    time per join:    0,106 ms
            Mapped vectors,          time per join:    0,845 ms
            Mapped matrix,           time per join:    1,779 ms
            Indexed vectors, x space time per join:    5,778 ms
            Indexed matrix, x space  time per join:    3,342 ms
            Mapped vectors, x space  time per join:    8,184 ms
            Mapped matrix, x space   time per join:   11,547 ms
            */
            /*
            Apple-M3-Pro-12c_2024-11-06T14-09-25
            Indexed unbound vectors, time per join:    0.035 ms
            Indexed unbound matrix,  time per join:    0.047 ms
            Indexed bound vectors,   time per join:    0.039 ms
            Indexed bound matrix,    time per join:    0.046 ms
            Mapped vectors,          time per join:    0.517 ms
            Mapped matrix,           time per join:    0.832 ms
            Indexed vectors, x space time per join:    3.734 ms
            Indexed matrix, x space  time per join:    2.426 ms
            Mapped vectors, x space  time per join:    4.893 ms
            Mapped matrix, x space   time per join:    6.153 ms
             */
        } catch (IOException e) {
            System.err.println("An error occurred while writing to the file: " + e.getMessage());
        }
    }

}
