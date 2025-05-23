// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.lightgbm;

import com.yahoo.json.Jackson;
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
    private Map<Integer, Boolean> categoricalIntegerFeatures = new HashMap<>();  // true if feature values are integers

    LightGBMParser(String filePath) throws IOException {
        ObjectMapper mapper = Jackson.createMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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

    /**
     * As primary method, we try to determine which are categorical features from the "features_infos" structure
     *  and then later use the "pandas_categorical" structure to determine the categoryValues.
     *  If we can't find the categorical features in the "features_infos" structure (models exported without
     *  "categorical_feature" parameter, or older versions of lightgbm), we fall back to traversing the
     *  decision tree looking for categorical decisions ('==') and use that to determine which features are categorical.
     *  Due to lack of documentation, we can't _know_ that the "feature_infos" and "pandas_categorical" will always
     *  be of the same length and in same order, but we have tried to verify this and not managed to find a case where they are not.
     *  And it is at least a lot better than the fallback, where we know some features can be
     *  treated incorrectly.
     */
    private Map<Integer, List<String>> parseCategoryValues(JsonNode root) {
        Map<Integer, List<String>> categoryValues = new HashMap<>();
        categoricalIntegerFeatures = new HashMap<>();  // Reset this map


        Set<Integer> categoricalFeatures = new TreeSet<>();
        // Get feature names and create a mapping from name to index
        List<String> featureNamesList = new ArrayList<>();
        if (root.has("feature_names") && root.get("feature_names").isArray()) {
            root.get("feature_names").forEach(nameNode -> featureNamesList.add(nameNode.asText()));
        }

        Map<String, Integer> featureNameToIndex = new HashMap<>();
        for (int i = 0; i < featureNamesList.size(); i++) {
            featureNameToIndex.put(featureNamesList.get(i), i);
        }

        JsonNode featureInfosNode = root.get("feature_infos");

        if (featureInfosNode != null && featureInfosNode.isObject()) {
            for (String featureName : featureNamesList) { // Iterate in the order of feature_names
                JsonNode specificFeatureInfo = featureInfosNode.get(featureName);
                if (specificFeatureInfo != null &&
                        specificFeatureInfo.has("values") &&
                        specificFeatureInfo.get("values").isArray() &&
                        !specificFeatureInfo.get("values").isEmpty()) {

                    Integer featureIndex = featureNameToIndex.get(featureName);
                    if (featureIndex != null) {
                        categoricalFeatures.add(featureIndex);
                    }
                }
            }
        }
        else {
            // We have no info in the model JSON to tell exactly which features are
            // categorical,so we fall back to traverse the decision tree looking for categorical
            // decisions ('==') and use that to determine which features are categorical.
            // NB: This might give incorrect results if the model has categorical features
            // that are not used in the decision tree. Solution is to upgrade to lightgbm>=4.0.0
            // and make sure that the "categorical_feature" parameter is passed when training the model.
            nodes.forEach(node -> findCategoricalFeatures(node, categoricalFeatures));
        }
        // Again, the LightGBM JSON format does not explicitly tell which
        // categorical values map to each categorical feature. The assumption
        // here is that the order they appear in the "pandas_categorical"
        // structure is the same order as the "feature_names" and the features
        // in the "feature_infos" structure.
        var pandasFeatureIterator = root.get("pandas_categorical").iterator();
        var categoricalFeatureIterator = categoricalFeatures.iterator();
        while (pandasFeatureIterator.hasNext() && categoricalFeatureIterator.hasNext()) {
            List<String> values = new ArrayList<>();
            JsonNode categoryNode = pandasFeatureIterator.next();
            int featureIndex = categoricalFeatureIterator.next();

            // Check if all values are integers
            boolean allIntegers = true;
            for (JsonNode value : categoryNode) {
                if (!value.isInt()) {
                    allIntegers = false;
                    break;
                }
            }

            // Store feature type
            categoricalIntegerFeatures.put(featureIndex, allIntegers);

            // Process values
            categoryNode.forEach(value -> {
                if (value.isTextual()) {
                    values.add(value.textValue());
                } else {
                    values.add(value.asText());
                }
            });

            categoryValues.put(featureIndex, values);
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
        int featureIndex = node.getSplit_feature();
        boolean isIntegerFeature = categoricalIntegerFeatures.getOrDefault(featureIndex, false);

        return Arrays.stream(node.getThreshold().split("\\|\\|"))
                .map(index -> {
                    String value = transformCategoryIndexToValue(featureIndex, index);
                    return isIntegerFeature ? value : "\"" + value + "\"";
                })
                .collect(Collectors.joining(","));
    }

    private String transformCategoryIndexToValue(int featureIndex, String valueIndex) {
        if ( ! categoryValues.containsKey(featureIndex) ) {
            return valueIndex;  // We don't have a pandas categorical lookup table
        }
        return categoryValues.get(featureIndex).get(Integer.parseInt(valueIndex));
    }

}
