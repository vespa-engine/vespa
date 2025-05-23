// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer.lightgbm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.json.Jackson;
import com.yahoo.path.Path;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.ArrayContext;
import com.yahoo.searchlib.rankingexpression.evaluation.StringValue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author lesters
 * @author thomasht86
 */
class LightGBMTestBase {

    RankingExpression importModel(String path) {
        return new LightGBMImporter().importModel("lightgbm", path).expressions().get("lightgbm");
    }

    void assertEvaluation(double expected, RankingExpression expr, TestFeatures features) {
        assertEquals(expected, expr.evaluate(features.context).asDouble(), 1e-6);
    }

    TestFeatures features(ArrayContext context) {
        return new TestFeatures(context.clone());
    }

    TestFeatures features(ArrayContext context, Map<String, FeatureValue> featureMap) {
        TestFeatures tf = new TestFeatures(context.clone());
        featureMap.forEach((name, value) -> {
            if (value != null) {
                // No need to add, as context has NaN as default value
                value.applyTo(tf, name);
            }
        });
        return tf;
    }

    static class TestFeatures {
        private final ArrayContext context;
        TestFeatures(ArrayContext context) {
            this.context = context;
        }
        TestFeatures add(String name, double value) {
            if (isValidFeatureName(name)) {
                context.put(name, value);
            }
            return this;
        }
        TestFeatures add(String name, String value) {
            if (isValidFeatureName(name)) {
                context.put(name, new StringValue(value));
            }
            return this;
        }
        private boolean isValidFeatureName(String name) {
            return this.context.names().contains(name);
        }
    }

    sealed interface FeatureValue permits StringFeatureValue, NumberFeatureValue {
        void applyTo(TestFeatures features, String name);
    }

    record StringFeatureValue(String value) implements FeatureValue {
        public void applyTo(TestFeatures features, String name) { features.add(name, value); }
        public String toString() { return value; }
    }

    record NumberFeatureValue(double value) implements FeatureValue {
        public void applyTo(TestFeatures features, String name) { features.add(name, value); }
        public String toString() { return String.valueOf(value); }
    }

    record TestCase(double expectedPrediction, Map<String, FeatureValue> features) {

        TestCase(Map<String, Object> raw, String targetFeature) {
            this(toDouble(raw.get(targetFeature)), extractFeatures(raw, targetFeature));
        }

        private static double toDouble(Object o) {
            if (o instanceof Number n) return n.doubleValue();
            throw new IllegalArgumentException("Test case missing or invalid model prediction");
        }

        private static Map<String, FeatureValue> extractFeatures(Map<String, Object> raw, String targetFeature) {
            return raw.entrySet().stream()
                    .filter(e -> !e.getKey().equals(targetFeature))
                    .filter(e -> e.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> toFeatureValue(e.getValue())));
        }

        private static FeatureValue toFeatureValue(Object v) {
            return (v instanceof String s) ? new StringFeatureValue(s)
                    : new NumberFeatureValue(((Number) v).doubleValue());
        }
    }

    List<TestCase> loadTestCasesFromJson(String path, String targetFeature) {
        ObjectMapper mapper = Jackson.createMapper();
        try {
            List<Map<String, Object>> raw = mapper.readValue(
                    Path.fromString(path).toFile(),
                    new TypeReference<>() {
                    });
            return raw.stream().map(r -> new TestCase(r, targetFeature)).collect(Collectors.toList());
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Failed to load test cases from " + path, e);
        }
    }
}
