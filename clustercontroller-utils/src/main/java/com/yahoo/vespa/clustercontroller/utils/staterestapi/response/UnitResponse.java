// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.response;

import java.util.Map;

public interface UnitResponse {
    public UnitAttributes getAttributes();
    public CurrentUnitState getCurrentState();
    public Map<String, SubUnitList> getSubUnits();
    public UnitMetrics getMetrics();
}
