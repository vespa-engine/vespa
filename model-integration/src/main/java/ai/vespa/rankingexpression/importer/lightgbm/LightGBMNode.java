// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.lightgbm;

/**
 * @author lesters
 */
public class LightGBMNode {

    // split nodes
    private int split_feature;
    private String threshold;  // double for numerical, string for categorical
    private String decision_type;
    private boolean default_left;
    private String missing_type;
    private int internal_count;
    private LightGBMNode left_child;
    private LightGBMNode right_child;

    // leaf nodes
    private double leaf_value;
    private int leaf_count;

    public int getSplit_feature() {
        return split_feature;
    }

    public String getThreshold() {
        return threshold;
    }

    public String getDecision_type() {
        return decision_type;
    }

    public boolean isDefault_left() {
        return default_left;
    }

    public String getMissing_type() {
        return missing_type;
    }

    public int getInternal_count() {
        return internal_count;
    }

    public LightGBMNode getLeft_child() {
        return left_child;
    }

    public LightGBMNode getRight_child() {
        return right_child;
    }

    public double getLeaf_value() {
        return leaf_value;
    }

    public int getLeaf_count() {
        return leaf_count;
    }

    public boolean isLeaf() {
        return left_child == null && right_child == null;
    }

}