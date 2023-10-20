package com.yahoo.vespa.hosted.controller.api.integration.billing;

/**
 * @author gjoranv
 */
public enum BillStatus {
    OPEN,       // All bills start in this state, the only state where changes can be made
    FINALIZED,  // No more changes can be made to the bill
    CLOSED,     // End state
    VOID;       // End state

    private static final String LEGACY_ISSUED = "ISSUED";         // Legacy state, used by historical bills
    private static final String LEGACY_EXPORTED = "EXPORTED";     // Legacy state, used by historical bills

    private final String value;

    BillStatus() {
        this.value = name();
    }

    public String value() {
        return value;
    }

    public static BillStatus from(String status) {
        if (LEGACY_ISSUED.equals(status) || LEGACY_EXPORTED.equals(status)) return OPEN;
        return Enum.valueOf(BillStatus.class, status.toUpperCase());
    }

}
