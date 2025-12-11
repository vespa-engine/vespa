// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.xgboost;

import com.devsmart.ubjson.UBArray;
import com.devsmart.ubjson.UBObject;
import com.devsmart.ubjson.UBReader;
import com.devsmart.ubjson.UBValue;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for XGBoost models in Universal Binary JSON (UBJ) format.
 *
 * @author arnej
 */
class XGBoostUbjParser extends AbstractXGBoostParser {

    private final List<XGBoostTree> xgboostTrees;
    private final double baseScore;

    /**
     * Constructor stores parsed UBJ trees.
     *
     * @param filePath XGBoost UBJ input file.
     * @throws IOException Fails file reading or UBJ parsing.
     */
    XGBoostUbjParser(String filePath) throws IOException {
        this.xgboostTrees = new ArrayList<>();
        double tmpBaseScore = 0.5; // default value
        try (FileInputStream fileStream = new FileInputStream(filePath);
             UBReader reader = new UBReader(fileStream)) {
            UBValue root = reader.read();

            UBArray forestArray;
            if (root.isArray()) {
                // Simple array format (like JSON export)
                forestArray = root.asArray();
            } else if (root.isObject()) {
                UBObject rootObj = root.asObject();
                UBObject learner = getRequiredObject(rootObj, "learner", "UBJ root");

                // Extract base_score if available
                tmpBaseScore = extractBaseScore(learner);

                // Navigate to trees array
                forestArray = navigateToTreesArray(learner);
            } else {
                throw new IOException("Expected UBJ array or object at root, got: " + root.getClass().getSimpleName());
            }

            // Parse each tree (UBJ format uses flat arrays, not nested objects)
            for (int i = 0; i < forestArray.size(); i++) {
                UBValue treeValue = forestArray.get(i);
                if (!treeValue.isObject()) {
                    throw new IOException("Expected UBJ object for tree, got: " + treeValue.getClass().getSimpleName());
                }
                this.xgboostTrees.add(convertUbjTree(treeValue.asObject()));
            }
        }
        this.baseScore = tmpBaseScore;
    }

    /**
     * Converts parsed UBJ trees to Vespa ranking expressions.
     *
     * @return Vespa ranking expressions.
     */
    String toRankingExpression() {
        StringBuilder result = new StringBuilder();

        // Convert all trees to expressions and join with " + "
        for (int i = 0; i < xgboostTrees.size(); i++) {
            if (i > 0) {
                result.append(" + \n");
            }
            result.append(treeToRankExp(xgboostTrees.get(i)));
        }

        // Add base_score logit transformation
        result.append(" + \n");
        result.append("log(").append(baseScore).append(") - log(").append(1.0 - baseScore).append(")");

        return result.toString();
    }

    /**
     * Extracts a required UBObject from a parent object.
     *
     * @param parent Parent UBObject to extract from.
     * @param key Key name to extract.
     * @param parentDescription Description of parent for error messages.
     * @return The extracted UBObject.
     * @throws IOException If the key is missing or not an object.
     */
    private static UBObject getRequiredObject(UBObject parent, String key, String parentDescription) throws IOException {
        UBValue value = parent.get(key);
        if (value == null || !value.isObject()) {
            throw new IOException("Expected '" + key + "' object in " + parentDescription);
        }
        return value.asObject();
    }

    /**
     * Extracts the base_score from learner_model_param if available.
     *
     * @param learner The learner UBObject.
     * @return The extracted base_score, or 0.5 if not found.
     */
    private static double extractBaseScore(UBObject learner) {
        UBValue learnerModelParamValue = learner.get("learner_model_param");
        if (learnerModelParamValue != null && learnerModelParamValue.isObject()) {
            UBObject learnerModelParam = learnerModelParamValue.asObject();
            UBValue baseScoreValue = learnerModelParam.get("base_score");
            if (baseScoreValue != null && baseScoreValue.isString()) {
                String baseScoreStr = baseScoreValue.asString();
                // Parse string like "[6.274165E-1]" - remove brackets and parse
                baseScoreStr = baseScoreStr.replace("[", "").replace("]", "");
                return Double.parseDouble(baseScoreStr);
            }
        }
        return 0.5; // default value
    }

    /**
     * Navigates from learner object to the trees array.
     *
     * @param learner The learner UBObject.
     * @return The trees UBArray.
     * @throws IOException If navigation fails.
     */
    private static UBArray navigateToTreesArray(UBObject learner) throws IOException {
        UBObject gradientBooster = getRequiredObject(learner, "gradient_booster", "learner");
        UBObject model = getRequiredObject(gradientBooster, "model", "gradient_booster");
        UBValue treesValue = model.get("trees");
        if (treesValue == null || !treesValue.isArray()) {
            throw new IOException("Expected 'trees' array in model");
        }
        return treesValue.asArray();
    }

    /**
     * Converts a UBJ tree (flat array format) to the root XGBoostTree node (hierarchical format).
     *
     * @param treeObj UBJ object containing flat arrays representing the tree.
     * @return Root XGBoostTree node with hierarchical structure.
     */
    private static XGBoostTree convertUbjTree(UBObject treeObj) {
        // Extract flat arrays from UBJ format
        int[] leftChildren = treeObj.get("left_children").asInt32Array();
        int[] rightChildren = treeObj.get("right_children").asInt32Array();
        float[] splitConditions = treeObj.get("split_conditions").asFloat32Array();
        int[] splitIndices = treeObj.get("split_indices").asInt32Array();
        float[] baseWeights = treeObj.get("base_weights").asFloat32Array();
        byte[] defaultLeftBytes = extractDefaultLeft(treeObj.get("default_left"));

        // Convert from flat arrays to hierarchical tree structure, starting at root (node 0, depth 0)
        return buildTreeFromArrays(0, 0, leftChildren, rightChildren, splitConditions,
                splitIndices, baseWeights, defaultLeftBytes);
    }

    /**
     * Extracts the default_left array from UBJ value.
     * Handles both UBArray and direct byte array formats.
     *
     * @param defaultLeftValue The UBValue containing default_left data.
     * @return Byte array with default_left values.
     */
    private static byte[] extractDefaultLeft(UBValue defaultLeftValue) {
        if (defaultLeftValue.isArray()) {
            // It's a UBArray, iterate and convert
            UBArray defaultLeftArray = defaultLeftValue.asArray();
            byte[] result = new byte[defaultLeftArray.size()];
            for (int i = 0; i < defaultLeftArray.size(); i++) {
                result[i] = defaultLeftArray.get(i).asByte();
            }
            return result;
        } else {
            return defaultLeftValue.asByteArray();
        }
    }

    /**
     * Recursively builds a hierarchical XGBoostTree from flat arrays.
     *
     * @param nodeId Current node index in the arrays.
     * @param depth Current depth in the tree (0 for root).
     * @param leftChildren Array of left child indices.
     * @param rightChildren Array of right child indices.
     * @param splitConditions Array of split threshold values.
     * @param splitIndices Array of feature indices to split on.
     * @param baseWeights Array of base weights (leaf values).
     * @param defaultLeft Array indicating if missing values go left.
     * @return XGBoostTree node.
     */
    private static XGBoostTree buildTreeFromArrays(int nodeId, int depth, int[] leftChildren, int[] rightChildren,
                                                   float[] splitConditions, int[] splitIndices,
                                                   float[] baseWeights, byte[] defaultLeft) {
        XGBoostTree node = new XGBoostTree();
        setField(node, "nodeid", nodeId);
        setField(node, "depth", depth);

        // Check if this is a leaf node
        boolean isLeaf = leftChildren[nodeId] == -1;

        if (isLeaf) {
            // Leaf node: set the leaf value from base_weights
            // Apply float rounding to match XGBoost's internal precision
            double leafValue = baseWeights[nodeId];
            setField(node, "leaf", leafValue);
        } else {
            // Split node: set split information
            int featureIdx = splitIndices[nodeId];
            setField(node, "split", "attribute(features," + featureIdx + ")");
            // Apply float rounding to match XGBoost's internal precision (same as XGBoostParser)
            double splitValue = splitConditions[nodeId];
            setField(node, "split_condition", splitValue);

            int leftChild = leftChildren[nodeId];
            int rightChild = rightChildren[nodeId];
            boolean goLeftOnMissing = defaultLeft[nodeId] != 0;

            // In XGBoost trees:
            // - Left child is taken when feature < threshold
            // - Right child is taken when feature >= threshold
            // - default_left only controls where missing values go
            setField(node, "yes", leftChild);   // yes = condition is true = feature < threshold = go left
            setField(node, "no", rightChild);   // no = condition is false = feature >= threshold = go right
            setField(node, "missing", goLeftOnMissing ? leftChild : rightChild);

            // Recursively build children
            List<XGBoostTree> children = new ArrayList<>();
            children.add(buildTreeFromArrays(leftChild, depth + 1, leftChildren, rightChildren,
                    splitConditions, splitIndices, baseWeights, defaultLeft));
            children.add(buildTreeFromArrays(rightChild, depth + 1, leftChildren, rightChildren,
                    splitConditions, splitIndices, baseWeights, defaultLeft));
            setField(node, "children", children);
        }

        return node;
    }

    /**
     * Uses reflection to set a private field on an object.
     *
     * @param obj Object to modify.
     * @param fieldName Name of the field to set.
     * @param value Value to set.
     */
    private static void setField(Object obj, String fieldName, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set field '" + fieldName + "' via reflection", e);
        }
    }

}
