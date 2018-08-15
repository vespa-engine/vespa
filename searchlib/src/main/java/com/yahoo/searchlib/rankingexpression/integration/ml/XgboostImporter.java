// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.xgboost.XGBoostParser;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;

import java.io.IOException;

/**
 * Converts a saved XGBoost model into a ranking expression.
 *
 * @author grace-lam
 */
public class XgboostImporter {

    public RankingExpression parseModel(String modelPath) {
        try {
            XGBoostParser parser = new XGBoostParser(modelPath);
            return new RankingExpression(parser.toRankingExpression());
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not import XGBoost model from '" + modelPath + "'", e);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Could not parse ranking expression: " + e);
        }
    }

}
