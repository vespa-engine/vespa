// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.requests;

import java.util.List;

public interface UnitRequest {

    List<String> getUnitPath();

}
