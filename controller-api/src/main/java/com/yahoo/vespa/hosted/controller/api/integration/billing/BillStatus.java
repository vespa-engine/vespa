package com.yahoo.vespa.hosted.controller.api.integration.billing;

/**
 * @author gjoranv
 */
public enum BillStatus {
    OPEN,       // All bills start in this state. The bill can be modified and exported/synced to external systems.
    FROZEN,     // Syncing to external systems is switched off. No changes can be made.
    CLOSED,     // End state for a valid bill.
    VOID;       // End state, indicating that the bill is not valid.

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
