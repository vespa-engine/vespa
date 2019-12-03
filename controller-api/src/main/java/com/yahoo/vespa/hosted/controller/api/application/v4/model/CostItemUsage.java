package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import java.math.BigDecimal;

/**
 * @author ogronnesby
 */
public class CostItemUsage {
    private BigDecimal usage;
    private BigDecimal charge;

    public CostItemUsage() {}

    public BigDecimal getUsage() {
        return usage;
    }

    public BigDecimal getCharge() {
        return charge;
    }
}
