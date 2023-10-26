package com.yahoo.vespa.hosted.controller.api.integration.billing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gjoranv
 */
public class BillStatusTest {

    @Test
    void legacy_states_are_converted() {
        assertEquals(BillStatus.OPEN, BillStatus.from("ISSUED"));
        assertEquals(BillStatus.OPEN, BillStatus.from("EXPORTED"));
        assertEquals(BillStatus.VOID, BillStatus.from("CANCELED"));
    }

}
