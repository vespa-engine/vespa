package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author valerijf
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArchivePatch {

    @JsonProperty("uri")
    private final String uri;

    @JsonCreator
    public ArchivePatch(@JsonProperty("uri") String uri) {
        this.uri = uri;
    }

    @JsonInclude
    public String uri() {
        return uri;
    }

}
