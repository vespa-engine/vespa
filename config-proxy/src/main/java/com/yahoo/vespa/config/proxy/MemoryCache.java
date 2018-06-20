// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
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

    public RawConfig get(ConfigCacheKey key) {
        return cache.get(key);
    }

    /**
     * Put in cache, except when config has an error
     *
     * @param config config to put in cache
     */
    public void put(RawConfig config) {
        if (config.isError()) return;

        log.log(LogLevel.DEBUG, () -> "Putting '" + config + "' into memory cache");
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
            log.log(LogLevel.DEBUG, "dumpCache. No path or empty path. Using dir '" + path + "'");
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        log.log(LogLevel.DEBUG, "Dumping cache to path '" + path + "'");
        IOUtils.createDirectory(path);
        File dir = new File(path);

        if (!dir.isDirectory() || !dir.canWrite()) {
            return "Not a dir or not able to write to '" + dir + "'";
        }
        for (RawConfig config : cache.values()) {
            writeConfigToFile(config, path);
        }
        return "success";
    }

    private void writeConfigToFile(RawConfig config, String path) {
        String filename = null;
        Writer writer = null;
        try {
            filename = path + File.separator + createCacheFileName(config);
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.log(LogLevel.DEBUG, "Writing '" + config.getKey() + "' to '" + filename + "'");
            }
            final Payload payload = config.getPayload();
            long protocolVersion = 3;
            log.log(LogLevel.DEBUG, "Writing config '" + config + "' to file '" + filename + "' with protocol version " + protocolVersion);
            writer = IOUtils.createWriter(filename, "UTF-8", false);

            // First three lines are meta-data about config as comment lines, fourth line is empty
            writer.write("# defMd5:" + config.getDefMd5() + "\n");
            writer.write("# configMd5:" + config.getConfigMd5() + "\n");
            writer.write("# generation:" + Long.toString(config.getGeneration()) + "\n");
            writer.write("# protocolVersion:" + Long.toString(protocolVersion) + "\n");
            writer.write("\n");
            writer.write(payload.withCompression(CompressionType.UNCOMPRESSED).toString());
            writer.write("\n");
            writer.close();
        } catch (IOException e) {
            log.log(LogLevel.WARNING, "Could not write to file '" + filename + "'");
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
