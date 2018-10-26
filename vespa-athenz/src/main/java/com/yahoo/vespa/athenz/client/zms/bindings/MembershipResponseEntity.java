// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zms.bindings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author bjorncs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MembershipResponseEntity {
    public final String memberName;
    public final boolean isMember;
    public final String roleName;
    public final String expiration;

    @JsonCreator
    public MembershipResponseEntity(@JsonProperty("memberName") String memberName,
                                    @JsonProperty("isMember") boolean isMember,
                                    @JsonProperty("roleName") String roleName,
                                    @JsonProperty("expiration") String expiration) {
        this.memberName = memberName;
        this.isMember = isMember;
        this.roleName = roleName;
        this.expiration = expiration;
    }
}
