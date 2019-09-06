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
