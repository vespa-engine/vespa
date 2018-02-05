// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.vespa.config.content.core.BucketspacesConfig;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class that based on BucketspacesConfig builds a mapping from document type to which bucket space it belongs to.
 */
class BucketSpaceEnumerator {

    private final Map<String, String> doctypeToSpace;

    private BucketSpaceEnumerator(String configId) {
        doctypeToSpace = Collections.unmodifiableMap(buildMappingFromConfig(configId));
    }

    public static BucketSpaceEnumerator fromConfig(String configId) {
        return new BucketSpaceEnumerator(configId);
    }

    public Map<String, String> getDoctypeToSpaceMapping() {
        return doctypeToSpace;
    }

    private static Map<String, String> buildMappingFromConfig(String configId) {
        BucketspacesConfig config = new ConfigGetter<>(BucketspacesConfig.class).getConfig(configId);
        return config.documenttype().stream().collect(Collectors.toMap(dt -> dt.name(), dt -> dt.bucketspace()));
    }

}
