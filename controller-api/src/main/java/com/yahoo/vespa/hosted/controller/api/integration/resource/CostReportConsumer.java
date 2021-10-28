// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.vespa.hosted.controller.api.identifiers.Property;

import java.util.Map;

/**
 * @author ldalves
 */
public interface CostReportConsumer {

    void consume(String csv);

    Map<Property, ResourceAllocation> fixedAllocations();

}
