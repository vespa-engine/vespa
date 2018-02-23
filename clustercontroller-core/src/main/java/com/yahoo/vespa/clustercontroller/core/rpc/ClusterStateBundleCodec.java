// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;

public interface ClusterStateBundleCodec {

    EncodedClusterStateBundle encode(ClusterStateBundle stateBundle);

    ClusterStateBundle decode(EncodedClusterStateBundle encodedClusterStateBundle);

}
