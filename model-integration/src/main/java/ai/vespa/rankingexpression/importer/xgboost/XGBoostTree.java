// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.xgboost;

import java.util.List;

/**
 * Outlines the JSON representation used for parsing the XGBoost output file.
 *
 * @author grace-lam
 */
public class XGBoostTree {

    // ID of current node.
    private int nodeid;
    // Depth of current node w.r.t. the tree's root.
    private int depth;
    // Feature name used for split.
    private String split;
    // Feature value threshold to split on.
    private double split_condition;
    // Next node if feature value < split_condition.
    private int yes;
    // Next node if feature value >= split_condition.
    private int no;
    // Next node if feature value is missing.
    private int missing;
    // Response value for leaf node.
    private double leaf;
    // List of child nodes.
    private List<XGBoostTree> children;

    public int getNodeid() {
        return nodeid;
    }

    public int getDepth() {
        return depth;
    }

    public String getSplit() {
        return split;
    }

    public double getSplit_condition() {
        return split_condition;
    }

    public int getYes() {
        return yes;
    }

    public int getNo() {
        return no;
    }

    public int getMissing() {
        return missing;
    }

    public double getLeaf() {
        return leaf;
    }

    public List<XGBoostTree> getChildren() {
        return children;
    }

    /**
     * Check if current node is a leaf node.
     *
     * @return True if leaf, false otherwise.
     */
    public boolean isLeaf() {
        return children == null;
    }

}
