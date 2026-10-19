// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlFunction;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModel;
import ai.vespa.rankingexpression.importer.configmodelview.MlModelImportCache;
import ai.vespa.rankingexpression.importer.configmodelview.MlModelImporter;
import com.yahoo.component.Vtag;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.io.IOUtils;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A cache of imported models stored on local disk, keyed by a hash of the model file content,
 * the model name, the importer and the Vespa version, so that unchanged models are not imported again
 * when an application is deployed again or when models for several Vespa versions are built.
 *
 * Paths are built as cacheRoot/vespaVersion/key/{model.json, constant-files}
 *
 * @author hmusum
 */
public class MlModelImportCacheImpl implements MlModelImportCache {

    private static final Logger log = Logger.getLogger(MlModelImportCacheImpl.class.getName());

    private static final int formatVersion = 1;

    private static final String modelFileName = "model.json";

    private final Path cacheDir;
    private final String vespaVersion;

    public MlModelImportCacheImpl(Path cacheRoot) {
        this(cacheRoot, Vtag.currentVersion.toFullString());
    }

    MlModelImportCacheImpl(Path cacheRoot, String vespaVersion) {
        this.vespaVersion = vespaVersion;
        this.cacheDir = cacheRoot.resolve(vespaVersion);
    }

    @Override
    public ImportedMlModel importModel(String modelName, File modelPath, MlModelImporter importer) {
        if ( ! importer.isCacheable()) return importer.importModel(modelName, modelPath);

        Optional<Path> entry = entry(modelName, modelPath, importer);
        Optional<ImportedMlModel> cached = entry.flatMap(e -> get(e, modelName, modelPath, importer));
        if (cached.isPresent()) return cached.get();

        ImportedMlModel model = importer.importModel(modelName, modelPath);
        entry.ifPresent(e -> put(e, modelName, modelPath, model));
        return model;
    }

    private Optional<Path> entry(String modelName, File modelPath, MlModelImporter importer) {
        try {
            return Optional.of(cacheDir.resolve(key(modelName, modelPath, importer)));
        }
        catch (IOException | RuntimeException e) {
            log.log(Level.INFO, "Could not compute cache key for model '" + modelName + "' from " + modelPath, e);
            return Optional.empty();
        }
    }

    private Optional<ImportedMlModel> get(Path entry, String modelName, File modelPath, MlModelImporter importer) {
        Path modelFile = entry.resolve(modelFileName);
        if ( ! Files.exists(modelFile)) return Optional.empty();

        try {
            ImportedMlModel model = read(entry, modelName, modelPath, importer);
            Files.setLastModifiedTime(modelFile, FileTime.from(Instant.now()));
            log.log(Level.FINE, () -> "Using cached import of model '" + modelName + "' from " + modelPath);
            return Optional.of(model);
        }
        catch (IOException | RuntimeException e) {
            log.log(Level.INFO, "Could not read cached import of model '" + modelName + "' from " + modelPath +
                                ", importing it instead", e);
            IOUtils.recursiveDeleteDir(entry.toFile());
            return Optional.empty();
        }
    }

    private void put(Path entry, String modelName, File modelPath, ImportedMlModel model) {
        if ( ! (model instanceof ImportedModel importedModel)) return;

        Path tmpEntry = null;
        try {
            Files.createDirectories(cacheDir);
            tmpEntry = cacheDir.resolve(entry.getFileName() + ".tmp-" + UUID.randomUUID());
            Files.createDirectory(tmpEntry);
            write(tmpEntry, importedModel);
            try {
                Files.move(tmpEntry, entry, StandardCopyOption.ATOMIC_MOVE);
                tmpEntry = null;
            }
            catch (FileAlreadyExistsException | DirectoryNotEmptyException e) {
                // Stored concurrently by someone else
            }
        }
        catch (IOException | RuntimeException e) {
            log.log(Level.INFO, "Could not store import of model '" + modelName + "' from " + modelPath + " in cache", e);
        }
        finally {
            if (tmpEntry != null)
                IOUtils.recursiveDeleteDir(tmpEntry.toFile());
        }
    }

    String key(String modelName, File modelPath, MlModelImporter importer) throws IOException {
        MessageDigest digest = sha256();
        digest.update(("format:" + formatVersion + "\n" +
                       "version:" + vespaVersion + "\n" +
                       "importer:" + importer.getClass().getName() + "\n" +
                       "name:" + modelName + "\n").getBytes(StandardCharsets.UTF_8));
        try (InputStream in = Files.newInputStream(modelPath.toPath())) {
            byte[] buffer = new byte[1 << 20];
            int read;
            while ((read = in.read(buffer)) != -1)
                digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void write(Path entry, ImportedModel model) throws IOException {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("name", model.name());
        root.setString("modelType", model.modelType().name());
        root.setBool("native", model.isNative());

        Cursor inputs = root.setObject("inputs");
        model.inputs().forEach((name, type) -> inputs.setString(name, type.toString()));

        Cursor functions = root.setObject("functions");
        model.functions().forEach(functions::setString);

        Cursor outputs = root.setArray("outputs");
        for (ImportedMlFunction function : model.outputExpressions()) {
            Cursor output = outputs.addObject();
            output.setString("name", function.name());
            Cursor arguments = output.setArray("arguments");
            function.arguments().forEach(arguments::addString);
            Cursor argumentTypes = output.setObject("argumentTypes");
            function.argumentTypes().forEach(argumentTypes::setString);
            output.setString("expression", function.expression());
            function.returnType().ifPresent(type -> output.setString("returnType", type));
        }

        writeConstants(entry, "small", model.smallConstantTensors(), root.setObject("smallConstants"));
        writeConstants(entry, "large", model.largeConstantTensors(), root.setObject("largeConstants"));

        Files.write(entry.resolve(modelFileName), SlimeUtils.toJsonBytes(slime));
    }

    /** Writes constants to files with generated names, as constant names may not be valid file names */
    private static void writeConstants(Path entry, String prefix, Map<String, Tensor> constants, Cursor fileNames) throws IOException {
        int i = 0;
        for (Map.Entry<String, Tensor> constant : constants.entrySet()) {
            String fileName = prefix + "-" + i++ + ".tbf";
            Files.write(entry.resolve(fileName), TypedBinaryFormat.encode(constant.getValue()));
            fileNames.setString(constant.getKey(), fileName);
        }
    }

    private static ImportedMlModel read(Path entry, String modelName, File modelPath, MlModelImporter importer) throws IOException {
        Inspector root = SlimeUtils.jsonToSlimeOrThrow(Files.readAllBytes(entry.resolve(modelFileName))).get();
        if ( ! modelName.equals(root.field("name").asString()))
            throw new IllegalStateException("Cached model has name '" + root.field("name").asString() +
                                            "', expected '" + modelName + "'");

        Map<String, String> inputs = new HashMap<>();
        root.field("inputs").traverse((String name, Inspector type) -> inputs.put(name, type.asString()));

        Map<String, String> functions = new HashMap<>();
        root.field("functions").traverse((String name, Inspector expression) -> functions.put(name, expression.asString()));

        List<ImportedMlFunction> outputs = new ArrayList<>();
        root.field("outputs").traverse((ArrayTraverser) (index, output) -> {
            List<String> arguments = new ArrayList<>();
            output.field("arguments").traverse((ArrayTraverser) (i, argument) -> arguments.add(argument.asString()));
            Map<String, String> argumentTypes = new HashMap<>();
            output.field("argumentTypes").traverse((String name, Inspector type) -> argumentTypes.put(name, type.asString()));
            outputs.add(new ImportedMlFunction(output.field("name").asString(),
                                               arguments,
                                               output.field("expression").asString(),
                                               argumentTypes,
                                               SlimeUtils.optionalString(output.field("returnType"))));
        });

        String source = modelPath.toString();
        Supplier<ImportedMlModel> nativeSupplier = () -> importer.importModel(modelName, modelPath).asNative();
        return new CachedMlModel(modelName,
                                 source,
                                 ImportedMlModel.ModelType.valueOf(root.field("modelType").asString()),
                                 inputs,
                                 readConstants(entry, root.field("smallConstants")),
                                 readConstants(entry, root.field("largeConstants")),
                                 functions,
                                 outputs,
                                 root.field("native").asBool(),
                                 nativeSupplier);
    }

    private static Map<String, Tensor> readConstants(Path entry, Inspector fileNames) {
        Map<String, Tensor> constants = new HashMap<>();
        fileNames.traverse((String name, Inspector fileName) -> {
            try {
                byte[] bytes = Files.readAllBytes(entry.resolve(fileName.asString()));
                constants.put(name, TypedBinaryFormat.decode(Optional.empty(), GrowableByteBuffer.wrap(bytes)));
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return constants;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
