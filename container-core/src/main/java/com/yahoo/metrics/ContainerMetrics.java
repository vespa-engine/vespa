package com.yahoo.metrics;

/**
 * @author gjoranv
 */
public enum ContainerMetrics {

    HTTP_STATUS_1XX("http.status.1xx", "Number of responses with a 1xx status"),
    HTTP_STATUS_2XX("http.status.2xx", "Number of responses with a 2xx status"),
    HTTP_STATUS_3XX("http.status.3xx", "Number of responses with a 3xx status"),
    HTTP_STATUS_4XX("http.status.4xx", "Number of responses with a 4xx status"),
    HTTP_STATUS_5XX("http.status.5xx", "Number of responses with a 5xx status"),
    JDISC_GC_MS("jdisc.gc.ms", "Time [ms] spent in garbage collection");

    private final String name;
    private final String description;

    ContainerMetrics(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String baseName() {
        return name;
    }

    public String description() {
        return description;
    }

    private String withSuffix(Suffix suffix) {
        return baseName() + "." + suffix.suffix();
    }

    public String ninety_five_percentile() {
        return withSuffix(Suffix.ninety_five_percentile);
    }

    public String ninety_nine_percentile() {
        return withSuffix(Suffix.ninety_nine_percentile);
    }

    public String average() {
        return withSuffix(Suffix.average);
    }

    public String count() {
        return withSuffix(Suffix.count);
    }

    public String last() {
        return withSuffix(Suffix.last);
    }

    public String max() {
        return withSuffix(Suffix.max);
    }

    public String min() {
        return withSuffix(Suffix.min);
    }

    public String rate() {
        return withSuffix(Suffix.rate);
    }

    public String sum() {
        return withSuffix(Suffix.sum);
    }

}