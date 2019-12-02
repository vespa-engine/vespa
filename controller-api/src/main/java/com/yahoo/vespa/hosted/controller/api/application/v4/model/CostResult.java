package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import java.util.List;

public class CostResult {
    private String month;
    private List<CostItem> items;

    public CostResult() {}

    public String getMonth() {
        return month;
    }

    public List<CostItem> getItems() {
        return items;
    }
}
