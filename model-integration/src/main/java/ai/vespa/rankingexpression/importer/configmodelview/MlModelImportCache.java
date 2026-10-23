// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.configmodelview;

import java.io.File;

/**
 * Imports models using a cache, to avoid importing the same model again.
 * Implementations must treat cache failures as cache misses.
 *
 * @author hmusum
 */
public interface MlModelImportCache {

    /** Returns the result of importing the model at the given path with the given importer, from cache if possible */
    ImportedMlModel importModel(String modelName, File modelPath, MlModelImporter importer);

    /** Returns a cache which never caches anything */
    static MlModelImportCache none() {
        return (modelName, modelPath, importer) -> importer.importModel(modelName, modelPath);
    }

}
