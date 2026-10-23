// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlFunction;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModel;
import com.yahoo.tensor.Tensor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * An imported model read from a {@link MlModelImportCacheImpl}.
 *
 * @author hmusum
 */
class CachedMlModel implements ImportedMlModel {

    private final String name;
    private final String source;
    private final ModelType modelType;
    private final Map<String, String> inputTypeSpecs;
    private final Map<String, Tensor> smallConstants;
    private final Map<String, Tensor> largeConstants;
    private final Map<String, String> functions;
    private final List<ImportedMlFunction> outputExpressions;
    private final boolean isNative;

    /** Supplies the native form of this, used if this is not native. Invoked at most once. */
    private final Supplier<ImportedMlModel> nativeSupplier;
    private ImportedMlModel nativeModel = null;

    CachedMlModel(String name, String source, ModelType modelType, Map<String, String> inputTypeSpecs,
                  Map<String, Tensor> smallConstants, Map<String, Tensor> largeConstants,
                  Map<String, String> functions, List<ImportedMlFunction> outputExpressions,
                  boolean isNative, Supplier<ImportedMlModel> nativeSupplier) {
        this.name = name;
        this.source = source;
        this.modelType = modelType;
        this.inputTypeSpecs = Map.copyOf(inputTypeSpecs);
        this.smallConstants = Map.copyOf(smallConstants);
        this.largeConstants = Map.copyOf(largeConstants);
        this.functions = Map.copyOf(functions);
        this.outputExpressions = List.copyOf(outputExpressions);
        this.isNative = isNative;
        this.nativeSupplier = nativeSupplier;
    }

    @Override public String name() { return name; }
    @Override public String source() { return source; }
    @Override public ModelType modelType() { return modelType; }
    @Override public Optional<String> inputTypeSpec(String input) { return Optional.ofNullable(inputTypeSpecs.get(input)); }
    @Override public Map<String, Tensor> smallConstantTensors() { return smallConstants; }
    @Override public Map<String, Tensor> largeConstantTensors() { return largeConstants; }
    @Override public Map<String, String> functions() { return functions; }
    @Override public List<ImportedMlFunction> outputExpressions() { return outputExpressions; }
    @Override public boolean isNative() { return isNative; }

    @Override
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    public Map<String, String> smallConstants() { return asStrings(smallConstants); }

    @Override
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    public Map<String, String> largeConstants() { return asStrings(largeConstants); }

    @Override
    public synchronized ImportedMlModel asNative() {
        if (isNative) return this;
        if (nativeModel == null)
            nativeModel = nativeSupplier.get();
        return nativeModel;
    }

    @Override
    public String toString() { return "cached imported model '" + name + "' from " + source; }

    private static Map<String, String> asStrings(Map<String, Tensor> map) {
        Map<String, String> values = new HashMap<>();
        map.forEach((key, value) -> values.put(key, value.toString()));
        return values;
    }

}
