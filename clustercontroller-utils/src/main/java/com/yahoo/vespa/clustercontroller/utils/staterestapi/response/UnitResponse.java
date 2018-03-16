// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.response;

import java.util.Map;

public interface UnitResponse {

    UnitAttributes getAttributes();
    CurrentUnitState getCurrentState();
    Map<String, SubUnitList> getSubUnits();
    UnitMetrics getMetrics();
    DistributionStates getDistributionStates();

}
