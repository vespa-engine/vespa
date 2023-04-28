package com.yahoo.vespa.athenz.identityprovider.api.bindings;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author olaa
 */
public record RolesEntity(@JsonProperty("roles") List<String> roles) {}
