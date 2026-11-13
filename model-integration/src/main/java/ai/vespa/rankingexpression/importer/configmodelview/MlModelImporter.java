// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.configmodelview;

import java.io.File;
import java.util.List;

/**
 * Config model view of a machine-learned model importer
 *
 * @author bratseth
 */
public interface MlModelImporter {

    boolean canImport(String modelPath);

    ImportedMlModel importModel(String modelName, File modelPath);

    /**
     * Returns true if the import result of this importer can be cached.
     * The result must then depend only on the model name, the content of the model file
     * and the content of the files that {@link #additionalInputFiles} returns.
     */
    default boolean isCacheable() { return false; }

    /**
     * An importer may read other files than the model file, such as a file with feature names.
     * Returns those other files for the model at the given path.
     * Every returned file must exist.
     */
    default List<File> additionalInputFiles(File modelPath) { return List.of(); }

}
