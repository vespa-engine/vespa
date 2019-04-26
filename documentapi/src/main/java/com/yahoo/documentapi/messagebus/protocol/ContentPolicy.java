// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import java.util.Map;

/**
 * Policy to talk to content clusters.
 */
public class ContentPolicy extends StoragePolicy {

    public static class ContentParameters extends Parameters {

        public ContentParameters(Map<String, String> parameters) {
            super(parameters);
        }

        @Override
        public String getDistributionConfigId() {
            if (distributionConfigId != null) {
                return distributionConfigId;
            }
            return clusterName;
        }

        @Override
        public SlobrokHostPatternGenerator createPatternGenerator() {
            return new SlobrokHostPatternGenerator(getClusterName()) {
                public String getDistributorHostPattern(Integer distributor) {
                    return "storage/cluster." + getClusterName() + "/distributor/" + (distributor == null ? "*" : distributor) + "/default";
                }
            };
        }
    }

    public ContentPolicy(Map<String, String> params) {
        super(new ContentParameters(params));
    }

    public ContentPolicy(String parameters) {
        this(parse(parameters));
    }

}
