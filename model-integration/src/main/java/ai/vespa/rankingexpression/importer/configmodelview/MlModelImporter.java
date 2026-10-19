// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.configmodelview;

import java.io.File;

/**
 * Config model view of a machine-learned model importer
 *
 * @author bratseth
 */
public interface MlModelImporter {

    boolean canImport(String modelPath);

    ImportedMlModel importModel(String modelName, File modelPath);

    /**
     * Returns true if the result of importing a model with this depends only on the model name and
     * the content of the model file, such that the result can be cached by file content.
     */
    default boolean isCacheable() { return false; }

}
