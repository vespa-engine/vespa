// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.configmodelview;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Config model view of an imported machine-learned model.
 *
 * @author bratseth
 */
public interface ImportedMlModel {

    enum ModelType {
        VESPA, XGBOOST, LIGHTGBM, TENSORFLOW, ONNX
    }

    String name();
    String source();
    ModelType modelType();

    Optional<String> inputTypeSpec(String input);
    Map<String, String> smallConstants();
    Map<String, String> largeConstants();
    Map<String, String> functions();
    List<ImportedMlFunction> outputExpressions();

    boolean isNative();
    ImportedMlModel asNative();
}
