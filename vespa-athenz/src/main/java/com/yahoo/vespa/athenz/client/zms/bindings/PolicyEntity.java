// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author olaa
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyEntity {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<AssertionEntity> assertions;
    private final String name;

    public PolicyEntity(@JsonProperty("name") String name,
                        @JsonProperty("assertions") List<AssertionEntity> assertions) {
        this.name = name;
        this.assertions = assertions;
    }

    public String getName() {
        return name;
    }

    public List<AssertionEntity> getAssertions() {
        return assertions;
    }
}
