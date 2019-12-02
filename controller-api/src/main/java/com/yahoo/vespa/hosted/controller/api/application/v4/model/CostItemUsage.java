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

    public static CostItemUsage zero() {
        var usage = new CostItemUsage();
        usage.charge = BigDecimal.ZERO;
        usage.usage = BigDecimal.ZERO;
        return usage;
    }

    public static CostItemUsage add(CostItemUsage a, CostItemUsage b) {
        var added = new CostItemUsage();
        added.usage = a.usage.add(b.usage);
        added.charge = a.charge.add(b.usage);
        return added;
    }

}
