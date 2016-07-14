// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

public enum NodeStateReason {
    NODE_TOO_UNSTABLE,
    WITHIN_MAINTENANCE_GRACE_PERIOD,
    FORCED_INTO_MAINTENANCE,
    GROUP_IS_DOWN
}
