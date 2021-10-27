// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.lightgbm;

import ai.vespa.rankingexpression.importer.ImportedModel;
import ai.vespa.rankingexpression.importer.ModelImporter;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModel;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;

import java.io.File;
import java.io.IOException;

/**
 * Converts a LightGBM model into a ranking expression.
 *
 * @author lesters
 */
public class LightGBMImporter extends ModelImporter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public boolean canImport(String modelPath) {
        File modelFile = new File(modelPath);
        if ( ! modelFile.isFile()) return false;
        return modelFile.toString().endsWith(".json") && probe(modelFile);
    }

    /**
     * Returns true if the give file looks like a LightGBM json file.
     * Currently, we just check if the json has an element called "tree_info"
     */
    private boolean probe(File modelFile) {
        try {
            JsonParser parser = objectMapper.createParser(modelFile);
            while (parser.nextToken() != null) {
                JsonToken token = parser.getCurrentToken();
                if (token == JsonToken.FIELD_NAME) {
                    if ("tree_info".equals(parser.getCurrentName())) return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read '" + modelFile + "'", e);
        }
    }

    @Override
    public ImportedModel importModel(String modelName, String modelPath) {
        try {
            ImportedModel model = new ImportedModel(modelName, modelPath, ImportedMlModel.ModelType.LIGHTGBM);
            LightGBMParser parser = new LightGBMParser(modelPath);
            RankingExpression expression = new RankingExpression(parser.toRankingExpression());
            model.expression(modelName, expression);
            return model;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not import LightGBM model from '" + modelPath + "'", e);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Could not parse ranking expression resulting from '" + modelPath + "'", e);
        }
    }

}
