// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;


import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Preconditions;

import java.util.Arrays;

public class JsonParserHelpers {

    public static void expectArrayStart(JsonToken token) {
        try {
            Preconditions.checkState(token == JsonToken.START_ARRAY, "Expected start of array, got %s", token);
        }
        catch (IllegalStateException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void expectArrayEnd(JsonToken token) {
        try {
            Preconditions.checkState(token == JsonToken.END_ARRAY, "Expected start of array, got %s", token);
        }
        catch (IllegalStateException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void expectObjectStart(JsonToken token) {
        try {
            Preconditions.checkState(token == JsonToken.START_OBJECT, "Expected start of JSON object, got %s", token);
        }
        catch (IllegalStateException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void expectObjectEnd(JsonToken token) {
        try {
            Preconditions.checkState(token == JsonToken.END_OBJECT, "Expected end of JSON object, got %s", token);
        }
        catch (IllegalStateException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void expectCompositeEnd(JsonToken token) {
        try {
            Preconditions.checkState(token.isStructEnd(), "Expected end of composite, got %s", token);
        }
        catch (IllegalStateException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void expectScalarValue(JsonToken token) {
        try {
            Preconditions.checkState(token.isScalarValue(), "Expected to be scalar value, got %s", token);
        }
        catch (IllegalStateException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void expectOneOf(JsonToken token, JsonToken ... tokens) {
        if (Arrays.stream(tokens).noneMatch(t -> t == token))
            throw new IllegalArgumentException("Expected one of " + tokens + " but got " + token);
    }

}
