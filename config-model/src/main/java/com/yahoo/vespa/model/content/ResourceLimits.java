// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.defaults.Defaults;

import java.util.Optional;

/**
 * Class tracking resource limits for a content cluster with engine proton.
 *
 * @author geirst
 */
public class ResourceLimits implements ProtonConfig.Producer {

    private final Optional<Double> diskLimit;
    private final Optional<Double> memoryLimit;

    private ResourceLimits(Builder builder) {
        this.diskLimit = builder.diskLimit;
        this.memoryLimit = builder.memoryLimit;
    }

    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        ProtonConfig.Writefilter.Builder writeFilterBuilder = new ProtonConfig.Writefilter.Builder();
        if (diskLimit.isPresent()) {
            writeFilterBuilder.disklimit(diskLimit.get());
        }
        if (memoryLimit.isPresent()) {
            writeFilterBuilder.memorylimit(memoryLimit.get());
        }
        builder.writefilter(writeFilterBuilder);
    }

    public static class Builder {

        private Optional<Double> diskLimit = Optional.empty();
        private Optional<Double> memoryLimit = Optional.empty();

        public ResourceLimits build() {
            return new ResourceLimits(this);
        }

        public Builder setDiskLimit(double diskLimit) {
            this.diskLimit = Optional.of(diskLimit);
            return this;
        }

        public Builder setMemoryLimit(double memoryLimit) {
            this.memoryLimit = Optional.of(memoryLimit);
            return this;
        }
    }
}
