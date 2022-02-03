// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author olaa
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyEntity {
    private static final Pattern namePattern = Pattern.compile("^(?<domain>[^:]+):policy\\.(?<name>.*)$");

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<AssertionEntity> assertions;
    private final String name;

    public PolicyEntity(@JsonProperty("name") String name,
                        @JsonProperty("assertions") List<AssertionEntity> assertions) {
        this.name = nameFromResourceString(name);
        this.assertions = assertions;
    }

    private static String nameFromResourceString(String resource) {
        Matcher matcher = namePattern.matcher(resource);
        if (!matcher.matches())
            throw new IllegalArgumentException("Could not find policy name from resource string: " + resource);
        return matcher.group("name");
    }

    public String getName() {
        return name;
    }

    public List<AssertionEntity> getAssertions() {
        return assertions;
    }
}
