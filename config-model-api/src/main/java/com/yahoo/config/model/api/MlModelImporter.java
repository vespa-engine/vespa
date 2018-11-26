package com.yahoo.config.model.api;

import java.io.File;

/**
 * Config model view of a machine-learned model importer
 *
 * @author bratseth
 */
public interface MlModelImporter {

    boolean canImport(String modelPath);

    ImportedMlModel importModel(String modelName, File modelPath);

}
