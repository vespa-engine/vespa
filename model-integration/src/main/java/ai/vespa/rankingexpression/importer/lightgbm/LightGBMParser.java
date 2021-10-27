// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.lightgbm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @author lesters
 */
class LightGBMParser {

    private final String objective;
    private final List<LightGBMNode> nodes;
    private final List<String> featureNames;
    private final Map<Integer, List<String>> categoryValues;  // pr feature index

    LightGBMParser(String filePath) throws JsonProcessingException, IOException {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JsonNode root = mapper.readTree(new File(filePath));

        objective = root.get("objective").asText("regression");
        featureNames = parseFeatureNames(root);
        nodes = parseTrees(mapper, root);
        categoryValues = parseCategoryValues(root);
    }

    private List<String> parseFeatureNames(JsonNode root) {
        List<String> features = new ArrayList<>();
        for (JsonNode name : root.get("feature_names")) {
            features.add(name.textValue());
        }
        return features;
    }

    private List<LightGBMNode> parseTrees(ObjectMapper mapper, JsonNode root) throws JsonProcessingException {
        List<LightGBMNode> nodes = new ArrayList<>();
        for (JsonNode treeNode : root.get("tree_info")) {
            nodes.add(mapper.treeToValue(treeNode.get("tree_structure"), LightGBMNode.class));
        }
        return nodes;
    }

    private Map<Integer, List<String>> parseCategoryValues(JsonNode root) {
        Map<Integer, List<String>> categoryValues = new HashMap<>();

        // Since the JSON format does not explicitly tell which features are
        // categorical, we traverse the decision tree looking for categorical
        // decisions and use that to determine which categorical features.
        Set<Integer> categoricalFeatures = new TreeSet<>();
        nodes.forEach(node -> findCategoricalFeatures(node, categoricalFeatures));

        // Again, the LightGBM JSON format does not explicitly tell which
        // categorical values map to each categorical feature. The assumption
        // here is that the order they appear in the "pandas_categorical"
        // structure is the same order as the "feature_names".
        var pandasFeatureIterator = root.get("pandas_categorical").iterator();
        var categoricalFeatureIterator = categoricalFeatures.iterator();
        while (pandasFeatureIterator.hasNext() && categoricalFeatureIterator.hasNext()) {
            List<String> values = new ArrayList<>();
            pandasFeatureIterator.next().forEach(value -> values.add(value.textValue()));
            categoryValues.put(categoricalFeatureIterator.next(), values);
        }

        return categoryValues;
    }

    private void findCategoricalFeatures(LightGBMNode node, Set<Integer> categoricalFeatures) {
        if (node == null || node.isLeaf()) {
            return;
        }
        if (node.getDecision_type().equals("==")) {
            categoricalFeatures.add(node.getSplit_feature());
        }
        findCategoricalFeatures(node.getLeft_child(), categoricalFeatures);
        findCategoricalFeatures(node.getRight_child(), categoricalFeatures);
    }

    String toRankingExpression() {
        return applyObjective(nodes.stream().map(this::nodeToRankingExpression).collect(Collectors.joining(" + \n")));
    }

    // See https://lightgbm.readthedocs.io/en/latest/Parameters.html#objective
    private String applyObjective(String expression) {
        if (objective.startsWith("binary") || objective.equals("cross_entropy")) {
            return "sigmoid(" + expression + ")";
        }
        if (objective.equals("poisson") || objective.equals("gamma") || objective.equals("tweedie")) {
            return "exp(" + expression + ")";
        }
        return expression;  // else: use expression directly
    }

    private String nodeToRankingExpression(LightGBMNode node) {
        if (node.isLeaf()) {
            return Double.toString(node.getLeaf_value());
        } else {
            String condition;
            String feature = featureNames.get(node.getSplit_feature());
            if (node.getDecision_type().equals("==")) {
                String values = transformCategoryIndexesToValues(node);
                if (node.isDefault_left()) {  // means go left (true) when isNan
                    condition = "isNan(" + feature + ") || (" + feature + " in [ " + values + "])";
                } else {
                    condition = feature + " in [" + values + "]";
                }
            } else {  // assumption: all other decision types are <=
                double value = Double.parseDouble(node.getThreshold());
                if (node.isDefault_left()) {
                    condition = "!(" + feature + " >= " + value + ")";
                } else {
                    condition = feature + " < " + value;
                }
            }
            String left = nodeToRankingExpression(node.getLeft_child());
            String right = nodeToRankingExpression(node.getRight_child());
            return "if (" + condition + ", " + left + ", " + right + ")";
        }
    }

    private String transformCategoryIndexesToValues(LightGBMNode node) {
        return Arrays.stream(node.getThreshold().split("\\|\\|"))
                .map(index -> "\"" + transformCategoryIndexToValue(node.getSplit_feature(), index) + "\"")
                .collect(Collectors.joining(","));
    }

    private String transformCategoryIndexToValue(int featureIndex, String valueIndex) {
        if ( ! categoryValues.containsKey(featureIndex) ) {
            return valueIndex;  // We don't have a pandas categorical lookup table
        }
        return categoryValues.get(featureIndex).get(Integer.parseInt(valueIndex));
    }

}