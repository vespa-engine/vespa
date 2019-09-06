package com.yahoo.vespa.hosted.controller.restapi.cost;

public interface CostReportConsumer {
    void Consume(String csv);
}
