// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.feature;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.configsource.exports.ConfigSource;
import com.yahoo.vespa.configsource.exports.FileContentSource;
import com.yahoo.vespa.configsource.exports.FileContentSupplier;
import com.yahoo.vespa.configsource.exports.JacksonDeserializer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Provides the Host Admin with named configs that can be modified real-time by
 * manipulating JSON config files.
 *
 * @see ConfigSource
 * @author hakon
 */
public class FeatureConfigSource extends AbstractComponent implements ConfigSource {
    /** The default directory to use for the config. */
    public static final Path DEFAULT_DIRECTORY = Paths.get("/etc/vespa/config.d");

    public static final String JSON_CONFIGFILE_SUFFIX = ".json";

    /** Avoid '.' and '/' since ID is supposed match filename, see {@link #getJsonPath(FeatureConfigId)}. */
    private static final Pattern DYNAMIC_CONFIG_ID_PATTERN = Pattern.compile("^[^./]+$");

    private final Path jsonDirectory;
    private final FileContentSource fileContentSource;

    @Inject
    public FeatureConfigSource() {
        this(DEFAULT_DIRECTORY);
    }

    public FeatureConfigSource(Path jsonDirectory) {
        this.jsonDirectory = jsonDirectory;
        this.fileContentSource = new FileContentSource();
    }

    /** Creates a supplier of the JSON config identified by id and using Jackson. */
    public <T> FeatureConfigSupplier<T> newJacksonSupplier(FeatureConfigId id, Class<T> jacksonClass) {
        Path path = getJsonPath(id);
        JacksonDeserializer<T> deserializer = new JacksonDeserializer<>(jacksonClass);
        FileContentSupplier<T> jacksonSupplier = fileContentSource.newSupplier(path, deserializer);
        return new FeatureConfigSupplier<>(jacksonSupplier);
    }

    private Path getJsonPath(FeatureConfigId configId) {
        // Note: FeatureConfigId should guarantee asString() won't cause IllegalArgumentException.
        String id = configId.asString();
        if (!DYNAMIC_CONFIG_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Refusing to use FeatureConfigId '" + id +
                    "' as part of filename");
        }

        return jsonDirectory.resolve(configId.asString() + JSON_CONFIGFILE_SUFFIX);
    }
}
