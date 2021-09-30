// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author olaa
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssertionEntity {

    private final String role;
    private final String resource;
    private final String action;
    private final Integer id;


    public AssertionEntity(String role, String resource, String action) {
        this(role, resource, action, null);
    }

    public AssertionEntity(@JsonProperty("role") String role,
                           @JsonProperty("resource") String resource,
                           @JsonProperty("action") String action,
                           @JsonProperty("id") Integer id) {
        this.role = role;
        this.resource = resource;
        this.action = action;
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }

    @JsonIgnore
    public int getId() {
        return id;
    }
}
