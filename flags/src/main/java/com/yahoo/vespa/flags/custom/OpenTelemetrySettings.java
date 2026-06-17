// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.config.provision.OpenTelemetryConfiguration;

/**
 * JSON value of the {@code opentelemetry-sdk} feature flag
 * (see {@link com.yahoo.vespa.flags.Flags#OPENTELEMETRY_SDK}).
 * Missing fields fall back to the disabled defaults.
 *
 * @author onur
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenTelemetrySettings implements OpenTelemetryConfiguration {

    private final boolean enabled;
    private final String endpoint;
    private final double samplingRatio;

    public static OpenTelemetrySettings createDisabled() {
        return new OpenTelemetrySettings(false, null, null);
    }

    @JsonCreator
    public OpenTelemetrySettings(@JsonProperty("enabled") Boolean enabled,
                                 @JsonProperty("endpoint") String endpoint,
                                 @JsonProperty("samplingRatio") Double samplingRatio) {
        this.enabled = enabled != null && enabled;
        this.endpoint = endpoint;
        this.samplingRatio = samplingRatio != null ? samplingRatio : 1.0;
    }

    @JsonGetter("enabled")       @Override public boolean enabled()      { return enabled; }
    @JsonGetter("endpoint")      @Override public String endpoint()      { return endpoint; }
    @JsonGetter("samplingRatio") @Override public double samplingRatio() { return samplingRatio; }

}
