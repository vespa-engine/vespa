// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.response;

import java.util.Map;

public interface SubUnitList {
    /** id to link map. */
    public Map<String, String> getSubUnitLinks();
    public Map<String, UnitResponse> getSubUnits();
}
