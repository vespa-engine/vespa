// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author hmusum
 */
public class MemoryCache {

    private static final Logger log = Logger.getLogger(MemoryCache.class.getName());

    // Separator in file names between different fields of config key
    private final static String separator = ":";
    private static final String DEFAULT_DUMP_DIR = Defaults.getDefaults().underVespaHome("var/vespa/cache/config");

    private final ConcurrentHashMap<ConfigCacheKey, RawConfig> cache = new ConcurrentHashMap<>(500, 0.75f);

    public Optional<RawConfig> get(ConfigCacheKey key) {
        return Optional.ofNullable(cache.get(key));
    }

    /**
     * Put in cache, except when config has an error
     *
     * @param config config to update in cache
     */
    public void update(RawConfig config) {
        // Do not cache errors
        if (config.isError()) return;

        // Do not cache empty configs (which have generation 0), remove everything in cache
        if (config.getGeneration() == 0) {
            cache.clear();
            return;
        }

        log.log(Level.FINE, () -> "Putting '" + config + "' into memory cache");
        cache.put(new ConfigCacheKey(config.getKey(), config.getDefMd5()), config);
    }

    boolean containsKey(ConfigCacheKey key) {
        return cache.containsKey(key);
    }

    Collection<RawConfig> values() {
        return cache.values();
    }

    public int size() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
    }

    @Override
    public String toString() {
        return cache.toString();
    }

    String dumpCacheToDisk(String path, MemoryCache cache) {
        if (path == null || path.isEmpty()) {
            path = DEFAULT_DUMP_DIR;
            log.log(Level.INFO, "dumpCache. No path or empty path. Using '" + path + "'");
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        File dir = new File(path);
        if ( ! dir.exists()) {
            log.log(Level.INFO, dir.getAbsolutePath() + " does not exist, creating it");
            try {
                Files.createDirectory(dir.toPath());
            } catch (IOException e) {
                return "Failed creating '" + dir.getAbsolutePath() + "', " + e.getClass().getSimpleName();
            }
        }

        if ( ! dir.isDirectory()) {
            return "Not a directory: '" + dir.getAbsolutePath() + "'";
        }
        if ( ! dir.canWrite()) {
            return "Not able to write to '" + dir.getAbsolutePath() + "'";
        }

        log.log(Level.INFO, "Dumping cache to '" + dir.getAbsolutePath() + "'");
        for (RawConfig config : cache.values()) {
            writeConfigToFile(config, path);
        }
        return "success";
    }

    private void writeConfigToFile(RawConfig config, String path) {
        String filename = path + File.separator + createCacheFileName(config);
        Writer writer = null;
        try {
            log.log(Level.FINE, () -> "Writing '" + config.getKey() + "' to '" + filename + "'");
            final Payload payload = config.getPayload();
            long protocolVersion = 3;
            log.log(Level.FINE, () -> "Writing config '" + config + "' to file '" + filename + "' with protocol version " + protocolVersion);
            writer = IOUtils.createWriter(filename, "UTF-8", false);

            // First three lines are meta-data about config as comment lines, fourth line is empty
            writer.write("# defMd5:" + config.getDefMd5() + "\n");
            writer.write("# config checksums:" + config.getPayloadChecksums() + "\n");
            writer.write("# generation:" + config.getGeneration() + "\n");
            writer.write("# protocolVersion:" + protocolVersion + "\n");
            writer.write("\n");
            writer.write(payload.withCompression(CompressionType.UNCOMPRESSED).toString());
            writer.write("\n");
            writer.close();
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not write to file '" + filename + "'");
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static String createCacheFileName(RawConfig config) {
        return createCacheFileName(new ConfigCacheKey(config.getKey(), config.getDefMd5()));
    }

    private static String createCacheFileName(ConfigCacheKey key) {
        final ConfigKey<?> configKey = key.getKey();
        return configKey.getNamespace() + "." + configKey.getName() + separator + configKey.getConfigId().replaceAll("/", "_") +
                separator + key.getDefMd5();
    }

}
