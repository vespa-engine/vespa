// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.Inspector;

import java.util.Optional;
import java.util.function.Function;

/**
 * Reusable serialization logic.
 *
 * @author mpolden
 */
public class Serializers {

    private Serializers() {}

    public static <T> Optional<T> optionalField(Inspector field, Function<String, T> fieldMapper) {
        return Optional.of(field).filter(Inspector::valid).map(Inspector::asString).map(fieldMapper);
    }

}
