package com.yahoo.vespa.hosted.controller.api.integration.billing;

/**
 * @author gjoranv
 */
public enum BillStatus {
    OPEN(),     // All bills start in this state, the only state where changes can be made
    READY(),    // No more changes can be made
    SENT(),     // Sent to the customer
    CLOSED,     // End state
    VOID,       // End state
    LEGACY_ISSUED("ISSUED"),     // Legacy state, should be removed when unused by any current bills
    LEGACY_EXPORTED("EXPORTED"); // Legacy state, should be removed when unused by any current bills

    private final String value;

    BillStatus() {
        this.value = name();
    }

    BillStatus(String value) {
        this.value = value;
    }

    public String value() {
        if (this == LEGACY_ISSUED || this == LEGACY_EXPORTED) return OPEN.value;
        return value;
    }

    public static BillStatus from(String status) {
        return Enum.valueOf(BillStatus.class, status.toUpperCase());
    }
}
