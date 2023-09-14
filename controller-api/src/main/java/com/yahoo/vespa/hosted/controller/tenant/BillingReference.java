package com.yahoo.vespa.hosted.controller.tenant;

import java.time.Instant;

public record BillingReference(String reference, Instant updated) {
}
