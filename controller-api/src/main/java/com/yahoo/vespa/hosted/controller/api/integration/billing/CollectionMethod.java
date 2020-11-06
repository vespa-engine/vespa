package com.yahoo.vespa.hosted.controller.api.integration.billing;

public enum CollectionMethod {
    NONE,
    EPAY,
    INVOICE,
    AUTO // Deprecated - this has never been serialized and can be removed in subsequent release
}
