// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.feature;

import com.yahoo.vespa.configsource.exports.ConfigSupplier;
import com.yahoo.vespa.configsource.exports.FileContentSupplier;

import java.util.Optional;

/**
 * @author hakon
 */
public class FeatureConfigSupplier<T> implements ConfigSupplier<T> {
    private final FileContentSupplier<T> jsonFileSupplier;

    public FeatureConfigSupplier(FileContentSupplier<T> jsonFileSupplier) {
        this.jsonFileSupplier = jsonFileSupplier;
    }

    @Override
    public Optional<T> getSnapshot() {
        return jsonFileSupplier.getSnapshot();
    }
}
