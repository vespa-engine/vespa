// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    private final Long id;
    private final String effect;


    public AssertionEntity(String role, String resource, String action, String effect) {
        this(role, resource, action, null, effect);
    }

    public AssertionEntity(@JsonProperty("role") String role,
                           @JsonProperty("resource") String resource,
                           @JsonProperty("action") String action,
                           @JsonProperty("id") Long id,
                           @JsonProperty("effect") String effect) {
        this.role = role;
        this.resource = resource;
        this.action = action;
        this.id = id;
        this.effect = effect;
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

    public String getEffect() {
        return effect;
    }

    @JsonIgnore
    public long getId() {
        return id;
    }
}
