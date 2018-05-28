// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.feature;

/**
 * @author hakon
 */
public class FeatureFlag extends FeatureConfig<FlagJson> {
    public FeatureFlag(FeatureConfigSource backend, String featureId) {
        this(backend, featureId, false);
    }

    public FeatureFlag(FeatureConfigSource backend, String featureId, boolean defaultValue) {
        super(backend, new FeatureConfigId(featureId), FlagJson.class, new FlagJson(defaultValue));
    }

    public boolean enabled() {
        return getFeatureConfigSnapshot().enabled();
    }
}
