// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import ai.vespa.rankingexpression.importer.configmodelview.MlModelImportCacheLayout;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class MlModelImportCacheMaintainerTest {

    private final ManualClock clock = new ManualClock();
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private Path cacheRoot;
    private MlModelImportCacheMaintainer maintainer;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        MaintainerTester tester = new MaintainerTester(clock, temporaryFolder, flagSource);
        cacheRoot = temporaryFolder.newFolder("ml-model-import-cache").toPath();
        maintainer = new MlModelImportCacheMaintainer(tester.applicationRepository(), tester.curator(), Duration.ofMinutes(1), cacheRoot);
    }

    @Test
    public void removes_entries_not_used_within_ttl() throws IOException {
        Instant now = clock.instant();
        Path oldEntry = entry("8.1.0", "old", now.minus(Duration.ofHours(7 * 24 + 1)));
        Path usedEntry = entry("8.1.0", "used", now.minus(Duration.ofHours(1)));
        // Directory itself is old, but model.json was recently used
        setModified(usedEntry, now.minus(Duration.ofDays(30)));

        assertEquals(0.0, maintainer.maintain(), 0.0);
        assertFalse(Files.exists(oldEntry));
        assertTrue(Files.exists(MlModelImportCacheLayout.modelFile(usedEntry)));
        assertTrue(Files.exists(usedEntry.resolve("large-0.tbf")));
        assertEquals(1, Files.list(oldEntry.getParent()).count()); // No trash left behind

        // TTL is changed by flag
        flagSource.withIntFlag(Flags.ML_MODEL_IMPORT_CACHE_TTL_HOURS.id(), 1);
        clock.advance(Duration.ofMinutes(1));
        maintainer.maintain();
        assertFalse(Files.exists(usedEntry));
    }

    @Test
    public void removes_old_incomplete_and_temporary_directories() throws IOException {
        Instant now = clock.instant();
        Instant old = now.minus(Duration.ofDays(8));
        Path oldIncomplete = dir("8.1.0", "incomplete", old);
        Path newIncomplete = dir("8.1.0", "incomplete2", now);
        Path oldTmp = dir(MlModelImportCacheLayout.tmpEntry(path("8.1.0", "a")), old);
        Path newTmp = dir(MlModelImportCacheLayout.tmpEntry(path("8.1.0", "b")), now);
        Path oldTrash = dir(MlModelImportCacheLayout.trashEntry(path("8.1.0", "c")), old);
        // An old tmp directory with a model file is expired based on the directory, not the model file
        Path oldTmpWithModel = entry(MlModelImportCacheLayout.tmpEntry(path("8.1.0", "d")), now);
        setModified(oldTmpWithModel, old);

        maintainer.maintain();
        assertFalse(Files.exists(oldIncomplete));
        assertTrue(Files.exists(newIncomplete));
        assertFalse(Files.exists(oldTmp));
        assertTrue(Files.exists(newTmp));
        assertFalse(Files.exists(oldTrash));
        assertFalse(Files.exists(oldTmpWithModel));
    }

    @Test
    public void removes_old_empty_version_directories() throws IOException {
        Instant now = clock.instant();
        Instant old = now.minus(Duration.ofDays(8));
        entry("8.1.0", "old", old);
        Path oldVersion = MlModelImportCacheLayout.versionDir(cacheRoot, "8.1.0");
        setModified(oldVersion, old);
        Path newEmptyVersion = Files.createDirectories(MlModelImportCacheLayout.versionDir(cacheRoot, "8.2.0"));

        maintainer.maintain();
        // Version directory is modified when its entry is removed, so it is removed in a later run
        assertTrue(Files.exists(oldVersion));
        setModified(oldVersion, old);
        maintainer.maintain();
        assertFalse(Files.exists(oldVersion));
        assertTrue(Files.exists(newEmptyVersion));
    }

    @Test
    public void ttl_of_zero_disables_removal() throws IOException {
        flagSource.withIntFlag(Flags.ML_MODEL_IMPORT_CACHE_TTL_HOURS.id(), 0);
        Path oldEntry = entry("8.1.0", "old", clock.instant().minus(Duration.ofDays(100)));

        assertEquals(0.0, maintainer.maintain(), 0.0);
        assertTrue(Files.exists(oldEntry));
    }

    @Test
    public void missing_cache_dir_is_ok() throws IOException {
        Files.delete(cacheRoot);
        assertEquals(0.0, maintainer.maintain(), 0.0);
    }

    private Path entry(String version, String key, Instant lastUsed) throws IOException {
        return entry(path(version, key), lastUsed);
    }

    private Path entry(Path path, Instant lastUsed) throws IOException {
        Path entry = dir(path, lastUsed);
        Path modelFile = Files.writeString(MlModelImportCacheLayout.modelFile(entry), "{}");
        Files.writeString(entry.resolve("large-0.tbf"), "tensor");
        setModified(modelFile, lastUsed);
        setModified(entry, lastUsed);
        return entry;
    }

    private Path dir(String version, String name, Instant modified) throws IOException {
        return dir(path(version, name), modified);
    }

    private Path dir(Path path, Instant modified) throws IOException {
        Path dir = Files.createDirectories(path);
        setModified(dir, modified);
        return dir;
    }

    private Path path(String version, String name) {
        return MlModelImportCacheLayout.versionDir(cacheRoot, version).resolve(name);
    }

    private static void setModified(Path path, Instant instant) throws IOException {
        Files.setLastModifiedTime(path, FileTime.from(instant));
    }

}
