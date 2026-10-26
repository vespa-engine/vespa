// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import ai.vespa.rankingexpression.importer.configmodelview.MlModelImportCacheLayout;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.modelfactory.PreparedModelsBuilder;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.IntFlag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Removes entries in the ML model import cache that have not been used for a time given by a feature flag.
 * See {@link MlModelImportCacheLayout} for the disk layout.
 *
 * @author hmusum
 */
public class MlModelImportCacheMaintainer extends ConfigServerMaintainer {

    private final Path cacheRoot;
    private final IntFlag ttlHours;

    MlModelImportCacheMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval) {
        this(applicationRepository, curator, interval,
             PreparedModelsBuilder.mlModelImportCacheDir(applicationRepository.configserverConfig()).toPath());
    }

    MlModelImportCacheMaintainer(ApplicationRepository applicationRepository, Curator curator, Duration interval, Path cacheRoot) {
        super(applicationRepository, curator, applicationRepository.flagSource(), applicationRepository.clock(), interval, false);
        this.cacheRoot = cacheRoot;
        this.ttlHours = Flags.ML_MODEL_IMPORT_CACHE_TTL_HOURS.bindTo(applicationRepository.flagSource());
    }

    @Override
    protected double maintain() {
        int hours = ttlHours.value();
        if (hours <= 0 || ! Files.isDirectory(cacheRoot)) return 0.0;

        Instant expiry = applicationRepository.clock().instant().minus(Duration.ofHours(hours));
        int attempts = 0;
        int failures = 0;
        int removed = 0;
        for (Path versionDir : list(cacheRoot)) {
            if ( ! Files.isDirectory(versionDir)) continue;

            for (Path entry : list(versionDir)) {
                if ( ! isExpired(entry, expiry)) continue;

                attempts++;
                if (remove(entry)) removed++;
                else failures++;
            }
            // Remove empty directories for old Vespa versions, but only when old, to avoid racing with a new entry being stored
            if (list(versionDir).isEmpty() && isOlderThan(versionDir, expiry)) {
                try {
                    Files.delete(versionDir);
                }
                catch (IOException e) {
                    log.log(Level.FINE, () -> "Could not remove " + versionDir + ": " + e.getMessage());
                }
            }
        }
        if (removed > 0)
            log.log(Level.INFO, "Removed " + removed + " ML model import cache entries not used in the last " + hours + " hours");

        return asSuccessFactorDeviation(attempts, failures);
    }

    /** Valid entries expire based on when model.json was last used, other directories based on their own modification time */
    private boolean isExpired(Path entry, Instant expiry) {
        Path modelFile = MlModelImportCacheLayout.modelFile(entry);
        if ( ! MlModelImportCacheLayout.isTmpEntry(entry) && ! MlModelImportCacheLayout.isTrashEntry(entry) && Files.exists(modelFile))
            return isOlderThan(modelFile, expiry);
        return isOlderThan(entry, expiry);
    }

    private static boolean isOlderThan(Path path, Instant instant) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(instant);
        }
        catch (IOException e) {
            return false; // Removed concurrently, or not accessible
        }
    }

    /**
     * Removes an entry by first moving it to a unique name, so that concurrent readers see either
     * the whole entry or none of it. Returns whether removal was successful.
     */
    private boolean remove(Path entry) {
        Path trash = entry;
        if ( ! MlModelImportCacheLayout.isTrashEntry(entry)) {
            trash = MlModelImportCacheLayout.trashEntry(entry);
            try {
                Files.move(entry, trash, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (NoSuchFileException e) {
                return true; // Already removed by someone else
            }
            catch (IOException e) {
                log.log(Level.INFO, "Could not remove ML model import cache entry " + entry + ": " + e.getMessage());
                return false;
            }
        }
        try (Stream<Path> paths = Files.walk(trash)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
        catch (IOException | UncheckedIOException e) {
            log.log(Level.INFO, "Could not delete " + trash + ": " + e.getMessage());
            return false;
        }
        return ! Files.exists(trash);
    }

    private static List<Path> list(Path dir) {
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.toList();
        }
        catch (IOException | UncheckedIOException e) {
            return List.of();
        }
    }

}
