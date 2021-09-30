// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Patchable data under Application
 *
 * @author bratseth
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicationPatch {

    @JsonProperty
    private final Double currentReadShare;

    @JsonProperty
    private final Double maxReadShare;

    @JsonCreator
    public ApplicationPatch(@JsonProperty("currentReadShare") Double currentReadShare,
                            @JsonProperty("maxReadShare") Double maxReadShare) {
        this.currentReadShare = currentReadShare;
        this.maxReadShare = maxReadShare;
    }

    public Double getCurrentReadShare() { return currentReadShare; }
    public Double getMaxReadShare() { return maxReadShare; }

}
