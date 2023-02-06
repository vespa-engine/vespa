package com.yahoo.metrics;

import java.util.List;

/**
 * @author yngveaasheim
 */
public enum StorageMetrics implements VespaMetrics {

    VDS_DATASTORED_ALLDISKS_BUCKETS("vds.datastored.alldisks.buckets", Unit.BUCKET, "Number of buckets managed"),
    VDS_DATASTORED_ALLDISKS_DOCS("vds.datastored.alldisks.docs", Unit.DOCUMENT, "Number of documents stored");

    private final String name;
    private final Unit unit;
    private final String description;

    StorageMetrics(String name, Unit unit, String description) {
        this.name = name;
        this.unit = unit;
        this.description = description;
    }

    public String baseName() {
        return name;
    }

    public Unit unit() {
        return unit;
    }

    public String description() {
        return description;
    }

}
