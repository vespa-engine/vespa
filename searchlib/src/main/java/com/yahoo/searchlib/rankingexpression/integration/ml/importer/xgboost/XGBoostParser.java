// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml.importer.xgboost;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author grace-lam
 */
public class XGBoostParser {

    private List<XGBoostTree> xgboostTrees;

    /**
     * Constructor stores parsed JSON trees.
     *
     * @param filePath XGBoost JSON output file.
     * @throws JsonProcessingException Fails JSON parsing.
     * @throws IOException             Fails file reading.
     */
    public XGBoostParser(String filePath) throws JsonProcessingException, IOException {
        this.xgboostTrees = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode forestNode = mapper.readTree(new File(filePath));
        for (JsonNode treeNode : forestNode) {
            this.xgboostTrees.add(mapper.treeToValue(treeNode, XGBoostTree.class));
        }
    }

    /**
     * Converts parsed JSON trees to Vespa ranking expressions.
     *
     * @return Vespa ranking expressions.
     */
    public String toRankingExpression() {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < xgboostTrees.size(); i++) {
            ret.append(treeToRankExp(xgboostTrees.get(i)));
            if (i != xgboostTrees.size() - 1) {
                ret.append(" + \n");
            }
        }
        return ret.toString();
    }

    /**
     * Recursive helper function for toRankingExpression().
     *
     * @param node XGBoost tree node to convert.
     * @return Vespa ranking expression for input node.
     */
    public String treeToRankExp(XGBoostTree node) {
        if (node.isLeaf()) {
            return Double.toString(node.getLeaf());
        } else {
            assert node.getChildren().size() == 2;
            String trueExp;
            String falseExp;
            if (node.getYes() == node.getChildren().get(0).getNodeid()) {
                trueExp = treeToRankExp(node.getChildren().get(0));
                falseExp = treeToRankExp(node.getChildren().get(1));
            } else {
                trueExp = treeToRankExp(node.getChildren().get(1));
                falseExp = treeToRankExp(node.getChildren().get(0));
            }
            return "if (" + node.getSplit() + " < " + Double.toString(node.getSplit_condition()) + ", " + trueExp + ", "
                    + falseExp + ")";
        }
    }

}