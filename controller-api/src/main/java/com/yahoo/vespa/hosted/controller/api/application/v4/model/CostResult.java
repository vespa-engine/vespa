// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import java.util.List;

/**
 * @author ogronnesby
 */
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
