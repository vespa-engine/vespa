// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.vespa.hosted.controller.api.identifiers.Property;

import java.util.Map;
import java.util.function.Consumer;

/**
 * @author ldalves
 */
public class CostReportConsumerMock implements CostReportConsumer {

    private final Consumer<String> csvConsumer;
    private final Map<Property, ResourceAllocation> fixedAllocations;

    public CostReportConsumerMock() {
        this.csvConsumer = (ignored) -> {};
        this.fixedAllocations = Map.of();
    }

    public CostReportConsumerMock(Consumer<String> csvConsumer, Map<Property, ResourceAllocation> fixedAllocations) {
        this.csvConsumer = csvConsumer;
        this.fixedAllocations = Map.copyOf(fixedAllocations);
    }

    @Override
    public void consume(String csv) {
        csvConsumer.accept(csv);
    }

    @Override
    public Map<Property, ResourceAllocation> fixedAllocations() {
        return fixedAllocations;
    }

}
