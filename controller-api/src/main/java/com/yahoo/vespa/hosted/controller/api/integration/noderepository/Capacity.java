// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author olaa
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Capacity {

    @JsonProperty("removalPossible")
    public boolean removalPossible;

    public boolean isRemovalPossible() {
        return removalPossible;
    }

}
