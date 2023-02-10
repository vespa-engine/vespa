// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.io.IOUtils;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Function;

class RequestUtils {

    static <T> T requireField(Inspector inspector, String field, Function<String, T> mapper) {
        return SlimeUtils.optionalString(inspector.field(field))
                .map(mapper::apply)
                .orElseThrow(() -> new IllegalArgumentException("Expected field \"" + field + "\" in request"));
    }

    static byte[] toJsonBytes(InputStream jsonStream) {
        try {
            return IOUtils.readBytes(jsonStream, 1000 * 1000);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
