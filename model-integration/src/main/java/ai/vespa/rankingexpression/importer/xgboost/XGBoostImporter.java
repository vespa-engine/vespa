// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.xgboost;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModel;
import com.yahoo.io.IOUtils;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import ai.vespa.rankingexpression.importer.ImportedModel;
import ai.vespa.rankingexpression.importer.ModelImporter;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;

import java.io.BufferedReader;
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

        return modelFile.toString().endsWith(".json") && probe(modelFile);
    }

    /**
     * Returns true if the give file looks like an XGBoost json file.
     * Currently, we just check if the file has an array on the top level.
     */
    private boolean probe(File modelFile) {
        try {
            BufferedReader reader = IOUtils.createReader(modelFile.getAbsolutePath());
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("[")) return true;
                if ( ! line.isEmpty()) return false;
            }
            return false;
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not read '" + modelFile + "'", e);
        }
    }

    @Override
    public ImportedModel importModel(String modelName, String modelPath) {
        try {
            ImportedModel model = new ImportedModel(modelName, modelPath, ImportedMlModel.ModelType.XGBOOST);
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
