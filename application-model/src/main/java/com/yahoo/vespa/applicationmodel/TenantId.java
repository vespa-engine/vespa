// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonValue;
import com.yahoo.config.provision.TenantName;

import java.util.Objects;

/**
 * @author bjorncs
 */
// TODO: Remove this and use TenantName instead (if you need it for the JSON stuff move it to that layer and don't let it leak)
public class TenantId {
    public static final TenantId HOSTED_VESPA = new TenantId("hosted-vespa");

    private final String id;

    public TenantId(String id) {
        this.id = id;
    }

    // Jackson's StdKeySerializer uses toString() (and ignores annotations) for objects used as Map keys.
    // Therefore, we use toString() as the JSON-producing method, which is really sad.
    @Override
    @JsonValue
    public String toString() {
        return id;
    }

    public String value() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantId tenantId = (TenantId) o;
        return Objects.equals(id, tenantId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
