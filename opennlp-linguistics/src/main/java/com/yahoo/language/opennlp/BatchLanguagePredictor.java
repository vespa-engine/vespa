// Main class for batch prediction
// Ensure OpenNlpDetector.java is compiled and in the classpath,
// along with its dependencies (vespa-linguistics, opennlp-tools)
// and resource files (langdetect-183.bin, language-subtags.txt).

package com.yahoo.language.opennlp;

import com.yahoo.language.detect.Detection;
import com.yahoo.language.detect.Hint;
import com.yahoo.language.opennlp.OpenNlpDetector; // Assuming OpenNlpDetector is in this package

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class BatchLanguagePredictor {

    public static void main(String[] args) {
        // Define input and output file paths
        Path inputFilePath = Paths.get("/Users/thomas/Repos/vespa/opennlp-linguistics/src/main/java/com/yahoo/language/opennlp/x_test.txt");
        Path outputFilePath = Paths.get("/Users/thomas/Repos/vespa/opennlp-linguistics/src/main/java/com/yahoo/language/opennlp/y_preds.txt");
        // Define hint (empty string)
        Hint hint = Hint.newInstance("", "");
        // --- IMPORTANT ---
        // Ensure the following files are in your classpath:
        // - /models/langdetect-183.bin
        // - /iana/language-subtags.txt
        // For example, if you have a 'resources' folder at the root of your classpath:
        // - resources/models/langdetect-183.bin
        // - resources/iana/language-subtags.txt
        // The OpenNlpDetector class loads these via getResourceAsStream.
        System.out.println("Initializing OpenNlpDetector...");
        System.out.println("Make sure 'langdetect-183.bin' and 'language-subtags.txt' are in the classpath under 'models/' and 'iana/' respectively.");
        OpenNlpDetector detector;
        try {
            detector = new OpenNlpDetector(); // Uses default confidence threshold
            // You can also use the constructor with a custom threshold:
            // detector = new OpenNlpDetector(2.0); // Example threshold
        } catch (Exception e) {
            System.err.println("Error initializing OpenNlpDetector. Ensure model files are in classpath.");
            e.printStackTrace();
            return;
        }
        System.out.println("OpenNlpDetector initialized.");

        List<String> linesToPredict = new ArrayList<>();
        List<String> predictedLanguages = new ArrayList<>();

        // Read lines from x_test.txt
        try (BufferedReader reader = Files.newBufferedReader(inputFilePath, StandardCharsets.UTF_8)) {
            String line;
            System.out.println("Reading input file: " + inputFilePath.toAbsolutePath());
            while ((line = reader.readLine()) != null) {
                linesToPredict.add(line);
            }
            System.out.println("Read " + linesToPredict.size() + " lines from input file.");
        } catch (IOException e) {
            System.err.println("Error reading input file '" + inputFilePath.toAbsolutePath() + "': " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (linesToPredict.isEmpty()) {
            System.out.println("No lines to predict from input file.");
            return;
        }

        // Perform predictions and measure time
        System.out.println("Starting predictions...");
        long startTime = System.nanoTime();

        for (String text : linesToPredict) {
            if (text == null || text.trim().isEmpty()) {
                predictedLanguages.add("UNKNOWN"); // Or handle as appropriate
                continue;
            }
            try {
                // Using no hint as no specific hint is provided in the scenario
                Detection detection = detector.detect(text, hint);
                predictedLanguages.add(detection.getLanguage().languageCode());
            } catch (Exception e) {
                System.err.println("Error detecting language for text: \"" + text.substring(0, Math.min(text.length(), 50)) + "...\" - " + e.getMessage());
                predictedLanguages.add("ERROR_DETECTING"); // Mark error
            }
        }

        long endTime = System.nanoTime();
        long totalTimeNano = endTime - startTime;
        long totalTimeMillis = TimeUnit.NANOSECONDS.toMillis(totalTimeNano);
        double totalTimeSeconds = totalTimeNano / 1_000_000_000.0;

        int numberOfPredictions = predictedLanguages.size();
        double avgTimePerPredictionMillis = (double) totalTimeNano / numberOfPredictions / 1_000_000.0;

        System.out.println("Predictions finished.");

        // Write predictions to y_preds.txt
        try (BufferedWriter writer = Files.newBufferedWriter(outputFilePath, StandardCharsets.UTF_8)) {
            System.out.println("Writing predictions to: " + outputFilePath.toAbsolutePath());
            for (String langCode : predictedLanguages) {
                writer.write(langCode);
                writer.newLine();
            }
            System.out.println("Successfully wrote " + numberOfPredictions + " predictions.");
        } catch (IOException e) {
            System.err.println("Error writing output file '" + outputFilePath.toAbsolutePath() + "': " + e.getMessage());
            e.printStackTrace();
        }

        // Print statistics
        System.out.println("\n--- Prediction Statistics ---");
        System.out.println("Total lines processed: " + numberOfPredictions);
        System.out.printf("Total time used: %.3f seconds (%d ms)\n", totalTimeSeconds, totalTimeMillis);
        if (numberOfPredictions > 0) {
            System.out.printf("Average time per prediction: %.4f ms\n", avgTimePerPredictionMillis);
        }
        System.out.println("--- End of Statistics ---");
    }
}