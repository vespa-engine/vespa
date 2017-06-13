// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * @author bjorncs
 */
// TODO: Remove this and use ApplicationName/InstanceName instead (if you need it for the JSON stuff move it to that layer and don't let it leak)
public class ApplicationInstanceId {
    private final String id;

    public ApplicationInstanceId(String id) {
        this.id = id;
    }

    // Jackson's StdKeySerializer uses toString() (and ignores annotations) for objects used as Map keys.
    // Therefore, we use toString() as the JSON-producing method, which is really sad.
    @Override
    @JsonValue
    public String toString() {
        return id;
    }

    // For compatibility with original Scala case class
    public String s() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationInstanceId that = (ApplicationInstanceId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
