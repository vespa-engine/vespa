package com.yahoo.vespa.hosted.controller.restapi.cost;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;

public class NoopCostReportConsumer implements CostReportConsumer {

    @Inject
    public NoopCostReportConsumer() {}

    @Override
    public void Consume(String csv) {
        // discard into the void
    }
}
