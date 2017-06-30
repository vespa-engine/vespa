package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * @author smorgrav
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllocationEnquiryResponse {
    @JsonProperty("clustercapacity")
    Map<String, Integer> clusterCapacity;
    @JsonProperty("flavorcapacity")
    Map<String, Integer> flavorCapacity;
    @JsonProperty("allocations")
    Map<String, Boolean> allocations;
}
