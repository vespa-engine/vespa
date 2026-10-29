// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.configmodelview;

import java.nio.file.Path;
import java.util.UUID;

/**
 * The disk layout of the cache of imported machine-learned models, shared by the cache ({@link MlModelImportCache})
 * and the config server maintainer (MlModelImportCacheMaintainer).
 * <p>
 * Paths used are cacheRoot/vespaVersion/key/{model.json, constant-files}. The modification time of
 * model.json is updated each time an entry is used.
 *
 * @author hmusum
 */
public final class MlModelImportCacheLayout {

    private static final String cacheDirName = "ml-model-import-cache";
    private static final String modelFileName = "model.json";
    private static final String tmpInfix = ".tmp-";
    private static final String trashInfix = ".trash-";

    private MlModelImportCacheLayout() {}

    /** Returns the root directory of the cache, given the parent directory to keep it in */
    public static Path cacheRoot(Path parent) { return parent.resolve(cacheDirName); }

    /** Returns the directory of entries for the given Vespa version */
    public static Path versionDir(Path cacheRoot, String vespaVersion) { return cacheRoot.resolve(vespaVersion); }

    /** Returns the model file of an entry, whose modification time is when the entry was last used */
    public static Path modelFile(Path entry) { return entry.resolve(modelFileName); }

    /** Returns a new, unique path for a temporary directory used when writing the given entry */
    public static Path newTmpEntry(Path entry) { return entry.resolveSibling(entry.getFileName() + tmpInfix + UUID.randomUUID()); }

    /** Returns a new, unique path for a trash directory used when removing the given entry */
    public static Path newTrashEntry(Path entry) { return entry.resolveSibling(entry.getFileName() + trashInfix + UUID.randomUUID()); }

    /** Returns whether the given path is a temporary directory */
    public static boolean isTmpEntry(Path path) { return path.getFileName().toString().contains(tmpInfix); }

    /** Returns whether the given path is a trash directory */
    public static boolean isTrashEntry(Path path) { return path.getFileName().toString().contains(trashInfix); }

}
