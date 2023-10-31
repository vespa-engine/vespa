package com.yahoo.vespa.hosted.controller.api.integration.billing;

/**
 * @author gjoranv
 */
public enum BillStatus {
    OPEN,        // All bills start in this state. The bill can be modified and exported/synced to external systems.
    FROZEN,      // Syncing to external systems is switched off. No changes can be made.
    SUCCESSFUL,  // Final state for a valid bill.
    VOID;        // Final state, indicating that the bill is not valid.

    // Legacy states, used by historical bills
    private static final String LEGACY_ISSUED = "ISSUED";
    private static final String LEGACY_EXPORTED = "EXPORTED";
    private static final String LEGACY_CANCELED = "CANCELED";

    private final String value;

    BillStatus() {
        this.value = name();
    }

    public String value() {
        return value;
    }

    /**
     * Returns true if the bill is in a final state.
     */
    public boolean isFinal() {
        return this == SUCCESSFUL || this == VOID;
    }

    public static BillStatus from(String status) {
        if (LEGACY_ISSUED.equals(status) || LEGACY_EXPORTED.equals(status)) return OPEN;
        if (LEGACY_CANCELED.equals(status)) return VOID;
        return Enum.valueOf(BillStatus.class, status.toUpperCase());
    }

}
