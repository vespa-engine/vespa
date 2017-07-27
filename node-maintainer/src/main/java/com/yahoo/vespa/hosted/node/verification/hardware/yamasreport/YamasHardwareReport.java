package com.yahoo.vespa.hosted.node.verification.hardware.yamasreport;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.node.verification.hardware.benchmarks.BenchmarkResults;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by sgrostad on 12/07/2017.
 * JSON-mapped class for reporting to YAMAS
 */
public class YamasHardwareReport {

    @JsonProperty
    private long timestamp;
    @JsonProperty
    private HardwareReportDimensions dimensions;
    @JsonProperty
    private HardwareReportMetrics metrics;
    @JsonProperty
    JsonObjectWrapper<JsonObjectWrapper<String[]>> routing;

    public YamasHardwareReport() {
        this.timestamp = System.currentTimeMillis() / 1000L;
        setRouting();
    }

    public HardwareReportDimensions getDimensions() {
        return dimensions;
    }

    public void setDimensions(HardwareReportDimensions dimensions) {
        this.dimensions = dimensions;
    }

    public HardwareReportMetrics getMetrics() {
        return metrics;
    }

    public void setMetrics(HardwareReportMetrics metrics) {
        this.metrics = metrics;
    }

    private void setRouting() {
        JsonObjectWrapper<String[]> wrap = new JsonObjectWrapper<>("namespace", new String[]{"Vespa"});
        routing = new JsonObjectWrapper<>("yamas", wrap);
    }

    public void createReportFromBenchmarkResults(BenchmarkResults benchmarkResults) {
        metrics = new HardwareReportMetrics();
        dimensions = new HardwareReportDimensions();
        metrics.setCpuCyclesPerSec(benchmarkResults.getCpuCyclesPerSec());
        metrics.setDiskSpeedMbs(benchmarkResults.getDiskSpeedMbs());
        metrics.setMemoryWriteSpeedGBs(benchmarkResults.getMemoryWriteSpeedGBs());
        metrics.setMemoryReadSpeedGBs(benchmarkResults.getMemoryReadSpeedGBs());
    }

    class JsonObjectWrapper<T> {
        private Map<String, T> wrappedObjects = new HashMap<String, T>();

        public JsonObjectWrapper(String name, T wrappedObject) {
            this.wrappedObjects.put(name, wrappedObject);
        }

        @JsonAnyGetter
        public Map<String, T> any() {
            return wrappedObjects;
        }

        @JsonAnySetter
        public void set(String name, T value) {
            wrappedObjects.put(name, value);
        }
    }

}
