// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.vespa.applicationmodel.ConfigId;

/**
 * Utility methods for creating test setups.
 *
 * @author bakksjo
 */
public class TestUtil {

    public static ConfigId storageNodeConfigId(String contentClusterName, int index) {
        return new ConfigId(contentClusterName + "/storage/" + index);
    }

    public static ConfigId clusterControllerConfigId(String contentClusterName, int index) {
        return new ConfigId(contentClusterName + "/standalone/" + contentClusterName + "-controllers/" + index);
    }

}
