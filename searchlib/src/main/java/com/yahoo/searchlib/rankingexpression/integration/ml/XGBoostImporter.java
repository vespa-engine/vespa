// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.xgboost.XGBoostParser;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;

import java.io.File;
import java.io.IOException;

/**
 * Converts a saved XGBoost model into a ranking expression.
 *
 * @author grace-lam
 * @author bratseth
 */
public class XGBoostImporter extends ModelImporter {

    @Override
    public boolean canImport(String modelPath) {
        File modelFile = new File(modelPath);
        if ( ! modelFile.isFile()) return false;

        return modelFile.toString().endsWith(".json"); // No other models ends by json yet
    }

    @Override
    public ImportedModel importModel(String modelName, String modelPath) {
        try {
            ImportedModel model = new ImportedModel(modelName, modelPath);
            XGBoostParser parser = new XGBoostParser(modelPath);
            RankingExpression expression = new RankingExpression(parser.toRankingExpression());
            model.expression(modelName, expression);
            return model;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not import XGBoost model from '" + modelPath + "'", e);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Could not parse ranking expression resulting from '" + modelPath + "'", e);
        }
    }

}
