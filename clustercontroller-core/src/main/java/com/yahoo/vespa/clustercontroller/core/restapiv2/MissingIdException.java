// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.MissingUnitException;

import java.util.List;

public class MissingIdException extends MissingUnitException {

    private static List<String> createPath(String cluster, Node n) {
        return List.of(cluster, n.getType().toString(), String.valueOf(n.getIndex()));
    }

    public MissingIdException(String cluster, Node n) {
        super(createPath(cluster, n), 2);
    }

}
