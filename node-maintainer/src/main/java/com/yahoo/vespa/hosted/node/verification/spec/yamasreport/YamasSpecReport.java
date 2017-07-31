package com.yahoo.vespa.hosted.node.verification.spec.yamasreport;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by olaa on 12/07/2017.
 */
public class YamasSpecReport {

    @JsonProperty
    private long timeStamp;
    @JsonProperty
    private SpecReportDimensions dimensions;
    @JsonProperty
    private SpecReportMetrics metrics;
    @JsonProperty
    private JsonObjectWrapper<JsonObjectWrapper<String[]>> routing;

    public YamasSpecReport() {
        this.timeStamp = System.currentTimeMillis() / 1000L;
        setRouting();
    }

    public void setDimensions(SpecReportDimensions dimensions) {
        this.dimensions = dimensions;
    }

    public SpecReportDimensions getDimensions() {
        return this.dimensions;
    }

    public void setMetrics(SpecReportMetrics metrics) {
        this.metrics = metrics;
    }

    public void setFaultyIpAddresses(String[] faultyIpAddresses) {
        this.metrics.setFaultyIpAddresses(faultyIpAddresses);
    }

    public SpecReportMetrics getMetrics() {
        return this.metrics;
    }

    public long getTimeStamp() {
        return this.timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    private void setRouting() {
        JsonObjectWrapper<String[]> wrap = new JsonObjectWrapper<>("namespace", new String[]{"Vespa"});
        routing = new JsonObjectWrapper<>("yamas", wrap);
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
