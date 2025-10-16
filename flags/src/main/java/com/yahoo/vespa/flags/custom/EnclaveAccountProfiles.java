// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public record EnclaveAccountProfiles(@JsonProperty("accounts") List<EnclaveAccountProfile> enclaveAccountProfiles) {
    public static EnclaveAccountProfiles EMPTY = new EnclaveAccountProfiles(List.of());
}
