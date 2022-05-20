// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author freva
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServicePublicKeyEntity {
    public final String id;
    public final String key;

    @JsonCreator
    public ServicePublicKeyEntity(@JsonProperty("id") String id, @JsonProperty("key") String key) {
        this.id = id;
        this.key = key;
    }

    @JsonGetter("id")
    public String name() {
        return id;
    }

    @JsonGetter("key")
    public String key() {
        return key;
    }
}
