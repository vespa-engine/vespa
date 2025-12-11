// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.xgboost;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.yahoo.json.Jackson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author grace-lam
 */
class XGBoostParser extends AbstractXGBoostParser {

    private final List<XGBoostTree> xgboostTrees;

    /**
     * Constructor stores parsed JSON trees.
     *
     * @param filePath XGBoost JSON intput file.
     * @throws JsonProcessingException Fails JSON parsing.
     * @throws IOException             Fails file reading.
     */
    XGBoostParser(String filePath) throws JsonProcessingException, IOException {
        this.xgboostTrees = new ArrayList<>();
        var mapper = Jackson.mapper();
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
    String toRankingExpression() {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < xgboostTrees.size(); i++) {
            ret.append(treeToRankExp(xgboostTrees.get(i)));
            if (i != xgboostTrees.size() - 1) {
                ret.append(" + \n");
            }
        }
        return ret.toString();
    }

}
