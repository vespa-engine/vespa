// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.MissingUnitException;

public class MissingIdException extends MissingUnitException {

    private static String[] createPath(String cluster, Node n) {
        String[] path = new String[3];
        path[0] = cluster;
        path[1] = n.getType().toString();
        path[2] = String.valueOf(n.getIndex());
        return path;
    }

    public MissingIdException(String cluster, Node n) {
        super(createPath(cluster, n), 2);
    }

}
