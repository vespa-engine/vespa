// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlFunction;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModel;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import ai.vespa.rankingexpression.importer.configmodelview.MlModelImporter;
import ai.vespa.rankingexpression.importer.lightgbm.LightGBMImporter;
import ai.vespa.rankingexpression.importer.onnx.OnnxImporter;
import ai.vespa.rankingexpression.importer.vespa.VespaImporter;
import ai.vespa.rankingexpression.importer.xgboost.XGBoostImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author hmusum
 */
public class MlModelImportCacheImplTest {

    private static final String version = "8.1.2";

    @Test
    void cached_models_are_equal_to_imported_models(@TempDir Path cacheRoot) {
        assertCachedEqualsImported(cacheRoot, new OnnxImporter(), "mnist", "src/test/models/onnx/mnist_softmax/mnist_softmax.onnx");
        assertCachedEqualsImported(cacheRoot, new XGBoostImporter(), "xgboost", "src/test/models/xgboost/xgboost.2.2.json");
        assertCachedEqualsImported(cacheRoot, new XGBoostImporter(), "xgboost_ubj", "src/test/models/xgboost/binary_breast_cancer.ubj");
        assertCachedEqualsImported(cacheRoot, new LightGBMImporter(), "lightgbm", "src/test/models/lightgbm/regression.json");
        assertCachedEqualsImported(cacheRoot, new NativeOnnxImporter(), "mnist_native", "src/test/models/onnx/mnist_softmax/mnist_softmax.onnx");
    }

    @Test
    void non_native_onnx_model_can_be_converted_to_native_from_cache(@TempDir Path cacheRoot) {
        var cache = new MlModelImportCacheImpl(cacheRoot, version);
        File modelFile = new File("src/test/models/onnx/mnist_softmax/mnist_softmax.onnx");
        cache.importModel("mnist", modelFile, new OnnxImporter());
        ImportedMlModel cached = cache.importModel("mnist", modelFile, new OnnxImporter());
        assertInstanceOf(CachedMlModel.class, cached);
        assertFalse(cached.isNative());
        assertModelsEqual(new OnnxImporter().importModel("mnist", modelFile).asNative(), cached.asNative());
    }

    @Test
    void only_successful_imports_with_cacheable_importers_are_cached(@TempDir Path tmp) throws IOException {
        Path cacheRoot = tmp.resolve("cache");
        var cache = new MlModelImportCacheImpl(cacheRoot, version);
        cache.importModel("example", new File("src/test/models/vespa/example.model"), new VespaImporter());
        assertEquals(0, entries(cacheRoot).size());

        Path invalid = Files.writeString(tmp.resolve("invalid.json"), "{ \"tree_info\": 1 }");
        assertThrows(RuntimeException.class, () -> cache.importModel("invalid", invalid.toFile(), new LightGBMImporter()));
        assertEquals(0, entries(cacheRoot).size());
    }

    @Test
    void key_depends_on_content_name_importer_and_version(@TempDir Path tmp) throws IOException {
        Path modelA = Files.writeString(tmp.resolve("a.json"), "[ 1 ]");
        Path modelB = Files.writeString(tmp.resolve("b.json"), "[ 2 ]");
        Path modelC = Files.writeString(tmp.resolve("c.json"), "[ 1 ]");
        var cache = new MlModelImportCacheImpl(tmp, version);
        String key = cache.key("m", modelA.toFile(), new XGBoostImporter());

        assertEquals(key, cache.key("m", modelC.toFile(), new XGBoostImporter()));
        assertNotEquals(key, cache.key("m", modelB.toFile(), new XGBoostImporter()));
        assertNotEquals(key, cache.key("n", modelA.toFile(), new XGBoostImporter()));
        assertNotEquals(key, cache.key("m", modelA.toFile(), new LightGBMImporter()));
        assertNotEquals(key, new MlModelImportCacheImpl(tmp, "8.1.3").key("m", modelA.toFile(), new XGBoostImporter()));
    }

    @Test
    void corrupt_entry_is_deleted_and_model_imported(@TempDir Path cacheRoot) throws IOException {
        var importer = new CountingImporter(new LightGBMImporter());
        var cache = new MlModelImportCacheImpl(cacheRoot, version);
        File modelFile = new File("src/test/models/lightgbm/regression.json");
        ImportedMlModel imported = cache.importModel("lightgbm", modelFile, importer);
        assertEquals(1, importer.imports.get());

        List<Path> entries = entries(cacheRoot);
        assertEquals(1, entries.size());
        Files.writeString(entries.get(0).resolve("model.json"), "not json");

        assertModelsEqual(imported, cache.importModel("lightgbm", modelFile, importer));
        assertEquals(2, importer.imports.get());
        assertModelsEqual(imported, cache.importModel("lightgbm", modelFile, importer));
        assertEquals(2, importer.imports.get());
    }

    @Test
    void imported_ml_models_uses_cache(@TempDir Path tmp) throws IOException {
        Path modelsDir = tmp.resolve("app/models");
        Files.createDirectories(modelsDir.resolve("sub"));
        Files.copy(Path.of("src/test/models/lightgbm/regression.json"), modelsDir.resolve("lightgbm.json"));
        Files.copy(Path.of("src/test/models/onnx/mnist_softmax/mnist_softmax.onnx"), modelsDir.resolve("sub/mnist.onnx"));
        var importers = List.<MlModelImporter>of(new CountingImporter(new OnnxImporter()),
                                                 new CountingImporter(new LightGBMImporter()));
        var cache = new MlModelImportCacheImpl(tmp.resolve("cache"), version);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = new ImportedMlModels(modelsDir.toFile(), executor, importers, cache);
            assertEquals(2, first.all().size());
            assertEquals(2, totalImports(importers));

            var second = new ImportedMlModels(modelsDir.toFile(), executor, importers, cache);
            assertEquals(2, second.all().size());
            assertEquals(2, totalImports(importers));
            for (ImportedMlModel model : first.all())
                assertModelsEqual(model, second.get(new File(model.source())));
        }
        finally {
            executor.shutdown();
        }
    }

    private static void assertCachedEqualsImported(Path cacheRoot, MlModelImporter importer, String name, String path) {
        var cache = new MlModelImportCacheImpl(cacheRoot, version);
        File modelFile = new File(path);
        ImportedMlModel imported = cache.importModel(name, modelFile, importer);
        assertFalse(imported instanceof CachedMlModel);
        ImportedMlModel cached = cache.importModel(name, modelFile, importer);
        assertInstanceOf(CachedMlModel.class, cached, "Model '" + name + "' should be read from cache");
        assertModelsEqual(imported, cached);
    }

    private static void assertModelsEqual(ImportedMlModel expected, ImportedMlModel actual) {
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.source(), actual.source());
        assertEquals(expected.modelType(), actual.modelType());
        assertEquals(expected.isNative(), actual.isNative());
        assertEquals(expected.functions(), actual.functions());
        assertEquals(expected.smallConstantTensors(), actual.smallConstantTensors());
        assertEquals(expected.largeConstantTensors(), actual.largeConstantTensors());
        assertEquals(asStrings(expected.outputExpressions()), asStrings(actual.outputExpressions()));
        for (ImportedMlFunction function : expected.outputExpressions())
            for (String argument : function.arguments())
                assertEquals(expected.inputTypeSpec(argument), actual.inputTypeSpec(argument));
    }

    private static List<String> asStrings(List<ImportedMlFunction> functions) {
        return functions.stream()
                        .map(f -> f.name() + "(" + f.arguments() + ", " + Map.copyOf(f.argumentTypes()) + ") -> " +
                                  f.returnType() + ": " + f.expression())
                        .sorted()
                        .collect(Collectors.toList());
    }

    private static List<Path> entries(Path cacheRoot) throws IOException {
        Path versionDir = cacheRoot.resolve(version);
        if ( ! Files.exists(versionDir)) return List.of();
        try (Stream<Path> files = Files.list(versionDir)) {
            return files.sorted(Comparator.naturalOrder()).toList();
        }
    }

    private static int totalImports(List<MlModelImporter> importers) {
        return importers.stream().mapToInt(importer -> ((CountingImporter) importer).imports.get()).sum();
    }

    /** Imports ONNX models in native form, to test caching of constants */
    private static class NativeOnnxImporter extends OnnxImporter {
        @Override
        public ImportedModel importModel(String modelName, String modelPath) {
            return super.importModel(modelName, modelPath).asNative();
        }
    }

    private static class CountingImporter implements MlModelImporter {

        private final MlModelImporter delegate;
        final AtomicInteger imports = new AtomicInteger();

        CountingImporter(MlModelImporter delegate) { this.delegate = delegate; }

        @Override public boolean canImport(String modelPath) { return delegate.canImport(modelPath); }
        @Override public boolean isCacheable() { return delegate.isCacheable(); }

        @Override
        public ImportedMlModel importModel(String modelName, File modelPath) {
            imports.incrementAndGet();
            return delegate.importModel(modelName, modelPath);
        }

    }

}
