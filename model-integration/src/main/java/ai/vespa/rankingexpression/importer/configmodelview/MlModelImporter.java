// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

}
