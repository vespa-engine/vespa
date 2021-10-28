// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.vespa;

import ai.vespa.rankingexpression.importer.ImportedModel;
import ai.vespa.rankingexpression.importer.ModelImporter;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModel;
import ai.vespa.rankingexpression.importer.vespa.parser.ModelParser;

import ai.vespa.rankingexpression.importer.vespa.parser.ParseException;
import ai.vespa.rankingexpression.importer.vespa.parser.SimpleCharStream;
import com.yahoo.io.IOUtils;

import java.io.File;
import java.io.IOException;

/**
 * Imports a model from a Vespa native ranking expression "model" file
 */
public class VespaImporter extends ModelImporter {

    @Override
    public boolean canImport(String modelPath) {
        File modelFile = new File(modelPath);
        if ( ! modelFile.isFile()) return false;

        return modelFile.toString().endsWith(".model");
    }

    @Override
    public ImportedModel importModel(String modelName, String modelPath) {
        try {
            ImportedModel model = new ImportedModel(modelName, modelPath, ImportedMlModel.ModelType.VESPA);
            new ModelParser(new SimpleCharStream(IOUtils.readFile(new File(modelPath))), model).model();
            return model;
        }
        catch (IOException | ParseException e) {
            throw new IllegalArgumentException("Could not import a Vespa model from '" + modelPath + "'", e);
        }
    }

}
